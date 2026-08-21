"use strict";

const crypto = require('crypto');

function generateRefreshToken() {
  return crypto.randomBytes(32).toString('base64url');
}

function hashRefreshToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function durationToMilliseconds(value) {
  const match = /^(\d+)([smhd])$/.exec(value);
  if (!match) throw new Error('Invalid authentication duration configuration');
  const units = { s: 1000, m: 60_000, h: 3_600_000, d: 86_400_000 };
  return Number(match[1]) * units[match[2]];
}

module.exports = { generateRefreshToken, hashRefreshToken, durationToMilliseconds };
