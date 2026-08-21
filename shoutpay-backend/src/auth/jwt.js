"use strict";

const jwt = require('jsonwebtoken');
const config = require('../config/config');

const JWT_ISSUER = 'shoutpay-backend';
const JWT_AUDIENCE = 'shoutpay-backend-api';

function signAccessToken({ merchantId, deviceId, sessionId }) {
  return jwt.sign(
    { deviceId, jti: sessionId },
    config.JWT_SECRET,
    { subject: merchantId, issuer: JWT_ISSUER, audience: JWT_AUDIENCE, expiresIn: config.AUTH_ACCESS_TOKEN_TTL }
  );
}

function verifyAccessToken(token, ignoreExpiration = false) {
  return jwt.verify(token, config.JWT_SECRET, {
    issuer: JWT_ISSUER,
    audience: JWT_AUDIENCE,
    ignoreExpiration,
  });
}

module.exports = { signAccessToken, verifyAccessToken };
