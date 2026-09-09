"use strict";

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');
const Database = require('./database/database');
const config = require('./config/config');
const { routerFor: authRouterFor } = require('./auth/router');
const { merchantRouterFor } = require('./modules/merchant/router');
const { devicesRouterFor } = require('./modules/devices/router');
const { transactionsRouterFor } = require('./modules/transactions/router');

const crypto = require('crypto');
const app = express();

// Safe request correlation — random UUID, never derived from PII/JWT
app.use((req, _res, next) => {
  req.requestId = req.get('X-Request-Id') || crypto.randomUUID();
  next();
});

// Middleware
app.use(helmet());
app.use(cors({
  origin: config.CORS_ORIGIN,
  credentials: true
}));
// Phase 8.1: verbose HTTP logging only in non-production. Production uses minimal/tiny to avoid leaking sensitive data.
if (config.NODE_ENV !== 'production') {
  app.use(morgan('combined'));
} else {
  app.use(morgan('tiny', {
    skip: (req) => req.path === '/health'
  }));
}
app.use(express.json({ limit: '256kb' }));
app.use(express.urlencoded({ extended: true, limit: '256kb' }));

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
app.use('/api/auth/devices', devicesRouterFor(database.client));
app.use('/api/merchant', merchantRouterFor(database.client));
app.use('/api/transactions', transactionsRouterFor(database.client));

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

// Error handling middleware — safe classification, no stack/body leak; includes requestId
app.use((err, req, res, next) => {
  const status = Number.isInteger(err.status) ? err.status : 500;
  const errorCode = status >= 500 ? 'INTERNAL_SERVER_ERROR' : (err.code || 'REQUEST_FAILED');
  if (config.NODE_ENV === 'production') {
    console.error('Request failed', { requestId: req.requestId, method: req.method, path: req.path, status, errorCode });
    return res.status(status).json({ error: { code: errorCode, requestId: req.requestId } });
  }
  console.error(err);
  return res.status(status).json({ error: { code: err.code || 'REQUEST_FAILED', message: err.message, requestId: req.requestId } });
});

// 404 handler — production does not echo probed path (enumeration resistance)
app.use('*', (req, res) => {
  if (config.NODE_ENV === 'production') {
    return res.status(404).json({ error: { code: 'NOT_FOUND' } });
  }
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
