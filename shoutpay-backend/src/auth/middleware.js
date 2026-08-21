"use strict";

const { verifyAccessToken } = require('./jwt');

function bearerToken(req) {
  const header = req.get('authorization');
  if (!header || !header.startsWith('Bearer ')) return null;
  return header.slice('Bearer '.length).trim() || null;
}

function requireAuth(req, res, next) {
  const token = bearerToken(req);
  if (!token) return res.status(401).json({ error: { code: 'UNAUTHORIZED' } });
  try {
    const payload = verifyAccessToken(token);
    if (typeof payload.sub !== 'string' || typeof payload.deviceId !== 'string' || typeof payload.jti !== 'string') {
      return res.status(401).json({ error: { code: 'UNAUTHORIZED' } });
    }
    req.auth = { merchantId: payload.sub, deviceId: payload.deviceId, sessionId: payload.jti };
    return next();
  } catch (_) {
    return res.status(401).json({ error: { code: 'UNAUTHORIZED' } });
  }
}

module.exports = { bearerToken, requireAuth };
