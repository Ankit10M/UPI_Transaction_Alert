#!/usr/bin/env node

const path = require('path');

// Load environment variables from .env.example
require('dotenv').config({ path: path.join(__dirname, '../.env.example') });

const app = require('./app');
const config = require('./config/config');

const server = app.listen(config.PORT, () => {
  console.log('ShoutPay Backend starting...');
  console.log(`Server running on port ${config.PORT}`);
  console.log(`Environment: ${config.NODE_ENV}`);
});

module.exports = server;