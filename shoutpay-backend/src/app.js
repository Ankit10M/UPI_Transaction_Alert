"use strict";

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');
const Database = require('./database/database');
const config = require('./config/config');
const { routerFor: authRouterFor, merchantRouterFor } = require('./auth/router');

const app = express();

// Middleware
app.use(helmet());
app.use(cors({
  origin: config.CORS_ORIGIN,
  credentials: true
}));
app.use(morgan('combined'));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Rate limiting
const limiter = rateLimit({
  windowMs: config.RATE_LIMIT_WINDOW_MS,
  max: config.RATE_LIMIT_MAX_REQUESTS,
  message: {
    error: 'Too many requests from this IP, please try again later.'
  }
});
app.use('/api/', limiter);

// Initialize database
const database = new Database();
app.use('/api/auth', authRouterFor(database.client));
app.use('/api/merchant', merchantRouterFor(database.client));

// Health check endpoint
app.get('/health', async (req, res) => {
  try {
    const dbStatus = await database.connect() ? 'connected' : 'disconnected';
    res.json({
      status: 'ok',
      service: 'shoutpay-backend',
      timestamp: new Date().toISOString(),
      database: dbStatus,
      uptime: process.uptime()
    });
  } catch (error) {
    res.status(500).json({
      status: 'error',
      service: 'shoutpay-backend',
      error: config.NODE_ENV === 'production' ? 'Service unavailable' : error.message,
      timestamp: new Date().toISOString()
    });
  }
});

// Error handling middleware
app.use((err, req, res, next) => {
  const status = Number.isInteger(err.status) ? err.status : 500;
  if (config.NODE_ENV === 'production') {
    console.error('Request failed', { method: req.method, path: req.path, status, errorName: err.name });
    return res.status(status).json({ error: { code: status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'REQUEST_FAILED' } });
  }
  console.error(err);
  return res.status(status).json({ error: { code: err.code || 'REQUEST_FAILED', message: err.message } });
});

// 404 handler
app.use('*', (req, res) => {
  res.status(404).json({
    error: {
      message: 'Route not found',
      path: req.originalUrl,
      timestamp: new Date().toISOString()
    }
  });
});

// Start server
async function startServer() {
  try {
    // Test database connection
    await database.connect();
    
    app.listen(config.PORT, () => {
      console.log(`ShoutPay Backend started on port ${config.PORT}`);
      console.log(`Environment: ${config.NODE_ENV}`);
      console.log(`Health check: http://localhost:${config.PORT}/health`);
    });
  } catch (error) {
    console.error('Failed to start server:', error);
    process.exit(1);
  }
}

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down gracefully...');
  await database.disconnect();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  console.log('Received SIGTERM, shutting down gracefully...');
  await database.disconnect();
  process.exit(0);
});

if (require.main === module) {
  startServer();
}

module.exports = app;
