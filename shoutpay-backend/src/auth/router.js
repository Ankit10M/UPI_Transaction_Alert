"use strict";

const crypto = require('crypto');
const express = require('express');
const rateLimit = require('express-rate-limit');
const Joi = require('joi');
const { verifyFirebaseIdToken } = require('./firebase');
const { signAccessToken, verifyAccessToken } = require('./jwt');
const { bearerToken, requireAuth } = require('./middleware');
const { durationToMilliseconds, generateRefreshToken, hashRefreshToken } = require('./tokens');
const config = require('../config/config');

const loginSchema = Joi.object({
  firebaseIdToken: Joi.string().trim().required(),
  deviceId: Joi.string().guid({ version: ['uuidv4'] }).required(),
  deviceName: Joi.string().trim().max(100).optional(),
});
const refreshSchema = Joi.object({ refreshToken: Joi.string().trim().min(32).required() });
const profileSchema = Joi.object({ ownerName: Joi.string().trim().min(1).max(120), shopName: Joi.string().trim().min(1).max(120) }).min(1);

function apiError(res, status, code) { return res.status(status).json({ error: { code } }); }
function expiry() { return new Date(Date.now() + durationToMilliseconds(config.AUTH_REFRESH_TOKEN_TTL)); }
function publicMerchant(merchant) { return { merchantId: merchant.merchantId, ownerName: merchant.ownerName, shopName: merchant.shopName, phoneNumber: merchant.phoneNumber }; }

async function allocateMerchantId(tx) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const merchantId = `SP-${crypto.randomInt(0, 1_000_000).toString().padStart(6, '0')}`;
    if (!await tx.merchant.findUnique({ where: { merchantId } })) return merchantId;
  }
  throw new Error('Unable to allocate merchant identifier');
}

function issueResponse(session, merchant, deviceId, refreshToken) {
  return { accessToken: signAccessToken({ merchantId: merchant.merchantId, deviceId, sessionId: session.id }), refreshToken, expiresIn: durationToMilliseconds(config.AUTH_ACCESS_TOKEN_TTL) / 1000, merchant: publicMerchant(merchant), deviceId };
}

async function serializableRefresh(prisma, operation) {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      return await prisma.$transaction(operation, { isolationLevel: 'Serializable' });
    } catch (error) {
      if (error.code !== 'P2034') throw error;
      if (attempt === 2) {
        const conflict = new Error('Refresh concurrency conflict');
        conflict.code = 'REFRESH_CONFLICT';
        throw conflict;
      }
    }
  }
}

function routerFor(prisma) {
  const router = express.Router();
  const authLimit = (max) => rateLimit({ windowMs: config.RATE_LIMIT_WINDOW_MS, max, standardHeaders: true, legacyHeaders: false, handler: (_, res) => apiError(res, 429, 'RATE_LIMITED') });

  router.post('/login', authLimit(5), async (req, res, next) => {
    const { error, value } = loginSchema.validate(req.body, { abortEarly: true, stripUnknown: true });
    if (error) return apiError(res, 400, 'MALFORMED_REQUEST');
    let decoded;
    try { decoded = await verifyFirebaseIdToken(value.firebaseIdToken); }
    catch (err) { return apiError(res, 401, err.code === 'auth/id-token-expired' ? 'ID_TOKEN_EXPIRED' : 'INVALID_ID_TOKEN'); }
    if (!decoded.uid || !decoded.phone_number) return apiError(res, 401, 'INVALID_ID_TOKEN');
    try {
      const result = await prisma.$transaction(async (tx) => {
        const existingDevice = await tx.device.findUnique({ where: { deviceId: value.deviceId } });
        let merchant = await tx.merchant.findUnique({ where: { firebaseUid: decoded.uid } });
        if (existingDevice && (!merchant || existingDevice.merchantId !== merchant.id)) return { conflict: true };
        if (!merchant) merchant = await tx.merchant.create({ data: { merchantId: await allocateMerchantId(tx), firebaseUid: decoded.uid, phoneNumber: decoded.phone_number, ownerName: 'Pending Owner', shopName: 'Pending Shop' } });
        const device = existingDevice || await tx.device.create({ data: { deviceId: value.deviceId, deviceName: value.deviceName, merchantId: merchant.id } });
        const tokenFamilyId = crypto.randomUUID(); const expiresAt = expiry(); const refreshToken = generateRefreshToken();
        const session = await tx.authSession.create({ data: { merchantId: merchant.id, deviceId: device.id, tokenFamilyId, expiresAt, lastUsedAt: new Date(), refreshTokens: { create: { tokenFamilyId, tokenHash: hashRefreshToken(refreshToken), expiresAt } } } });
        return { merchant, session, refreshToken };
      });
      if (result.conflict) return apiError(res, 409, 'DEVICE_CONFLICT');
      return res.status(200).json(issueResponse(result.session, result.merchant, value.deviceId, result.refreshToken));
    } catch (error) { return next(error); }
  });

  router.post('/refresh', authLimit(20), async (req, res, next) => {
    const { error, value } = refreshSchema.validate(req.body, { abortEarly: true, stripUnknown: true });
    if (error) return apiError(res, 400, 'MALFORMED_REQUEST');
    try {
      const result = await serializableRefresh(prisma, async (tx) => {
        const now = new Date(); const token = await tx.refreshToken.findUnique({ where: { tokenHash: hashRefreshToken(value.refreshToken) }, include: { session: { include: { merchant: true, device: true } } } });
        if (!token) return { invalid: true };
        if (token.usedAt) { await tx.refreshToken.updateMany({ where: { tokenFamilyId: token.tokenFamilyId }, data: { revokedAt: now } }); await tx.authSession.update({ where: { id: token.sessionId }, data: { revokedAt: now } }); return { replay: true }; }
        if (token.revokedAt || token.expiresAt <= now || token.session.revokedAt || token.session.expiresAt <= now) return { invalid: true };
        const consumed = await tx.refreshToken.updateMany({ where: { id: token.id, usedAt: null, revokedAt: null }, data: { usedAt: now } });
        if (consumed.count !== 1) { const current = await tx.refreshToken.findUnique({ where: { id: token.id } }); if (current && current.usedAt) { await tx.refreshToken.updateMany({ where: { tokenFamilyId: token.tokenFamilyId }, data: { revokedAt: now } }); await tx.authSession.update({ where: { id: token.sessionId }, data: { revokedAt: now } }); return { replay: true }; } return { invalid: true }; }
        const refreshToken = generateRefreshToken(); const expiresAt = expiry();
        await tx.refreshToken.create({ data: { sessionId: token.sessionId, tokenFamilyId: token.tokenFamilyId, tokenHash: hashRefreshToken(refreshToken), expiresAt } });
        const session = await tx.authSession.update({ where: { id: token.sessionId }, data: { lastUsedAt: now } });
        return { session, merchant: token.session.merchant, deviceId: token.session.device.deviceId, refreshToken };
      });
      if (result.replay) return apiError(res, 401, 'TOKEN_REPLAY_DETECTED');
      if (result.invalid) return apiError(res, 401, 'INVALID_REFRESH_TOKEN');
      return res.json(issueResponse(result.session, result.merchant, result.deviceId, result.refreshToken));
    } catch (error) { if (error.code === 'REFRESH_CONFLICT') return apiError(res, 503, 'REFRESH_CONFLICT'); return next(error); }
  });

  router.post('/logout', async (req, res, next) => {
    const token = bearerToken(req); if (!token) return apiError(res, 401, 'UNAUTHORIZED');
    try { const payload = verifyAccessToken(token, true); if (typeof payload.jti !== 'string') return apiError(res, 401, 'UNAUTHORIZED'); const now = new Date(); await prisma.$transaction([prisma.authSession.updateMany({ where: { id: payload.jti }, data: { revokedAt: now } }), prisma.refreshToken.updateMany({ where: { sessionId: payload.jti }, data: { revokedAt: now } })]); return res.status(204).send(); }
    catch (error) { if (error.name === 'JsonWebTokenError' || error.name === 'NotBeforeError') return apiError(res, 401, 'UNAUTHORIZED'); return next(error); }
  });

  router.post('/logout-all', requireAuth, async (req, res, next) => {
    try { const merchant = await prisma.merchant.findUnique({ where: { merchantId: req.auth.merchantId } }); if (!merchant) return res.status(204).send(); const sessions = await prisma.authSession.findMany({ where: { merchantId: merchant.id }, select: { id: true } }); const now = new Date(); await prisma.$transaction([prisma.authSession.updateMany({ where: { merchantId: merchant.id }, data: { revokedAt: now } }), prisma.refreshToken.updateMany({ where: { sessionId: { in: sessions.map((s) => s.id) } }, data: { revokedAt: now } })]); return res.status(204).send(); } catch (error) { return next(error); }
  });

  router.get('/me', requireAuth, async (req, res, next) => {
    try { const merchant = await prisma.merchant.findUnique({ where: { merchantId: req.auth.merchantId }, include: { devices: true } }); if (!merchant) return apiError(res, 404, 'MERCHANT_NOT_FOUND'); return res.json({ merchant: publicMerchant(merchant), devices: merchant.devices.map((device) => ({ deviceId: device.deviceId, deviceName: device.deviceName, activeStatus: device.activeStatus, lastSyncTime: device.lastSyncTime })) }); } catch (error) { return next(error); }
  });

  return router;
}

function merchantRouterFor(prisma) {
  const router = express.Router();
  router.patch('/profile', requireAuth, async (req, res, next) => {
    const { error, value } = profileSchema.validate(req.body, { abortEarly: true, stripUnknown: true }); if (error) return apiError(res, 400, 'MALFORMED_REQUEST');
    try { const merchant = await prisma.merchant.update({ where: { merchantId: req.auth.merchantId }, data: value }); return res.json({ merchant: publicMerchant(merchant) }); } catch (error) { return next(error); }
  });
  return router;
}

module.exports = { routerFor, merchantRouterFor, serializableRefresh };
