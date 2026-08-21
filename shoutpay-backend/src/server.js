#!/usr/bin/env node

const path = require('path');

// Load the real local .env file (gitignored) for runtime configuration.
// .env.example is a template/documentation file only and must not contain real credentials.
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const app = require('./app');
const config = require('./config/config');

config.assertAuthConfiguration();

const server = app.listen(config.PORT, () => {
  console.log('ShoutPay Backend starting...');
  console.log(`Server running on port ${config.PORT}`);
  console.log(`Environment: ${config.NODE_ENV}`);
});

module.exports = server;
