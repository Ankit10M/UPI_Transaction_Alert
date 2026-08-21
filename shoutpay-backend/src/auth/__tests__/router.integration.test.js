"use strict";

process.env.JWT_SECRET = 'integration-test-secret';
process.env.AUTH_ACCESS_TOKEN_TTL = '15m';
process.env.AUTH_REFRESH_TOKEN_TTL = '30d';

jest.mock('../firebase', () => ({ verifyFirebaseIdToken: jest.fn() }));

const express = require('express');
const request = require('supertest');
const { verifyFirebaseIdToken } = require('../firebase');
const { routerFor, serializableRefresh } = require('../router');
const { signAccessToken } = require('../jwt');

const DEVICE_ID = '123e4567-e89b-42d3-a456-426614174000';
const merchant = { id: 'merchant-db-1', merchantId: 'SP-123456', firebaseUid: 'firebase-uid-1', phoneNumber: '+911234567890', ownerName: 'Pending Owner', shopName: 'Pending Shop' };
const device = { id: 'device-db-1', deviceId: DEVICE_ID };

function appFor(prisma) {
  const app = express();
  app.use(express.json());
  app.use('/auth', routerFor(prisma));
  app.use((err, _req, res, _next) => res.status(500).json({ error: { code: err.code || 'INTERNAL' } }));
  return app;
}

function loginPrisma({ existingMerchant = null, existingDevice = null } = {}) {
  const tx = {
    merchant: {
      findUnique: jest.fn(async ({ where }) => where.firebaseUid ? existingMerchant : null),
      create: jest.fn(async ({ data }) => ({ ...data, id: merchant.id })),
    },
    device: { findUnique: jest.fn(async () => existingDevice), create: jest.fn(async ({ data }) => ({ ...data, id: device.id })) },
    authSession: { create: jest.fn(async () => ({ id: 'session-1' })) },
  };
  return { $transaction: jest.fn(async (callback) => callback(tx)), tx };
}

function refreshPrisma(token) {
  const tx = {
    refreshToken: {
      findUnique: jest.fn(async () => token),
      updateMany: jest.fn(async () => ({ count: 1 })),
      create: jest.fn(async () => ({})),
    },
    authSession: { update: jest.fn(async () => ({ id: 'session-1' })) },
  };
  return { $transaction: jest.fn(async (callback) => typeof callback === 'function' ? callback(tx) : Promise.all(callback)), tx };
}

beforeEach(() => {
  verifyFirebaseIdToken.mockReset();
  verifyFirebaseIdToken.mockResolvedValue({ uid: merchant.firebaseUid, phone_number: merchant.phoneNumber });
});

test('Firebase login accepts a valid verified token', async () => {
  const prisma = loginPrisma();
  const response = await request(appFor(prisma)).post('/auth/login').send({ firebaseIdToken: 'valid', deviceId: DEVICE_ID });
  expect(response.status).toBe(200);
  expect(response.body.merchant.merchantId).toMatch(/^SP-\d{6}$/);
});

test('Firebase login rejects invalid and expired tokens', async () => {
  verifyFirebaseIdToken.mockRejectedValueOnce(Object.assign(new Error(), { code: 'auth/invalid-id-token' }));
  expect((await request(appFor(loginPrisma())).post('/auth/login').send({ firebaseIdToken: 'bad', deviceId: DEVICE_ID })).status).toBe(401);
  verifyFirebaseIdToken.mockRejectedValueOnce(Object.assign(new Error(), { code: 'auth/id-token-expired' }));
  const response = await request(appFor(loginPrisma())).post('/auth/login').send({ firebaseIdToken: 'expired', deviceId: DEVICE_ID });
  expect(response.body.error.code).toBe('ID_TOKEN_EXPIRED');
});

test('Firebase login rejects a missing token', async () => {
  expect((await request(appFor(loginPrisma())).post('/auth/login').send({ deviceId: DEVICE_ID })).status).toBe(400);
});

test('first login bootstraps server-generated pending merchant', async () => {
  const prisma = loginPrisma();
  await request(appFor(prisma)).post('/auth/login').send({ firebaseIdToken: 'valid', deviceId: DEVICE_ID });
  const data = prisma.tx.merchant.create.mock.calls[0][0].data;
  expect(data).toMatchObject({ firebaseUid: merchant.firebaseUid, phoneNumber: merchant.phoneNumber, ownerName: 'Pending Owner', shopName: 'Pending Shop' });
  expect(data.merchantId).toMatch(/^SP-\d{6}$/);
});

test('second login reuses the merchant and same device', async () => {
  const prisma = loginPrisma({ existingMerchant: merchant, existingDevice: { ...device, merchantId: merchant.id } });
  expect((await request(appFor(prisma)).post('/auth/login').send({ firebaseIdToken: 'valid', deviceId: DEVICE_ID })).status).toBe(200);
  expect(prisma.tx.merchant.create).not.toHaveBeenCalled();
  expect(prisma.tx.device.create).not.toHaveBeenCalled();
});

test('a device claimed by a different merchant returns DEVICE_CONFLICT', async () => {
  const prisma = loginPrisma({ existingMerchant: merchant, existingDevice: { ...device, merchantId: 'other-merchant' } });
  const response = await request(appFor(prisma)).post('/auth/login').send({ firebaseIdToken: 'valid', deviceId: DEVICE_ID });
  expect(response.status).toBe(409);
  expect(response.body.error.code).toBe('DEVICE_CONFLICT');
});

function activeToken(overrides = {}) {
  return { id: 'token-a', sessionId: 'session-1', tokenFamilyId: 'family-1', usedAt: null, revokedAt: null, expiresAt: new Date(Date.now() + 60_000), session: { revokedAt: null, expiresAt: new Date(Date.now() + 60_000), merchant, device }, ...overrides };
}

test('refresh rotates a valid token', async () => {
  const { hashRefreshToken } = require('../tokens'); const raw = 'a'.repeat(43); const prisma = refreshPrisma(activeToken());
  const response = await request(appFor(prisma)).post('/auth/refresh').send({ refreshToken: raw });
  expect(response.status).toBe(200);
  expect(prisma.tx.refreshToken.create).toHaveBeenCalled();
  expect(prisma.tx.refreshToken.create.mock.calls[0][0].data.tokenHash).not.toBe(raw);
  expect(hashRefreshToken(raw)).toHaveLength(64);
});

test('a spent token triggers replay and revokes its family/session', async () => {
  const prisma = refreshPrisma(activeToken({ usedAt: new Date() }));
  const response = await request(appFor(prisma)).post('/auth/refresh').send({ refreshToken: 'a'.repeat(43) });
  expect(response.body.error.code).toBe('TOKEN_REPLAY_DETECTED');
  expect(prisma.tx.refreshToken.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: { tokenFamilyId: 'family-1' } }));
  expect(prisma.tx.authSession.update).toHaveBeenCalled();
});

test('serializable refresh retries conflicts at most three times and returns a controlled conflict', async () => {
  const conflict = Object.assign(new Error('serialization'), { code: 'P2034' });
  const prisma = { $transaction: jest.fn().mockRejectedValue(conflict) };
  await expect(serializableRefresh(prisma, async () => ({}))).rejects.toMatchObject({ code: 'REFRESH_CONFLICT' });
  expect(prisma.$transaction).toHaveBeenCalledTimes(3);
});

test('concurrent refresh requests cannot issue two replacement tokens', async () => {
  const token = activeToken();
  const tx = {
    refreshToken: {
      findUnique: jest.fn(async () => ({ ...token, usedAt: token.usedAt })),
      updateMany: jest.fn(async ({ where, data }) => {
        if (where.id) {
          if (token.usedAt) return { count: 0 };
          token.usedAt = data.usedAt;
          return { count: 1 };
        }
        return { count: 1 };
      }),
      create: jest.fn(async () => ({})),
    },
    authSession: { update: jest.fn(async () => ({ id: 'session-1' })) },
  };
  const prisma = { $transaction: jest.fn(async (callback) => callback(tx)) };
  const app = appFor(prisma);
  const [first, second] = await Promise.all([
    request(app).post('/auth/refresh').send({ refreshToken: 'a'.repeat(43) }),
    request(app).post('/auth/refresh').send({ refreshToken: 'a'.repeat(43) }),
  ]);
  expect([first.status, second.status].sort()).toEqual([200, 401]);
  expect(tx.refreshToken.create).toHaveBeenCalledTimes(1);
});

test('logout revokes its session and refresh tokens', async () => {
  const prisma = { authSession: { updateMany: jest.fn(() => Promise.resolve({})) }, refreshToken: { updateMany: jest.fn(() => Promise.resolve({})) }, $transaction: jest.fn((operations) => Promise.all(operations)) };
  const token = signAccessToken({ merchantId: merchant.merchantId, deviceId: DEVICE_ID, sessionId: 'session-1' });
  expect((await request(appFor(prisma)).post('/auth/logout').set('Authorization', `Bearer ${token}`)).status).toBe(204);
  expect(prisma.authSession.updateMany).toHaveBeenCalled(); expect(prisma.refreshToken.updateMany).toHaveBeenCalled();
});

test('logout-all revokes every merchant session and refresh token', async () => {
  const prisma = {
    merchant: { findUnique: jest.fn(async () => merchant) },
    authSession: { findMany: jest.fn(async () => [{ id: 'session-1' }, { id: 'session-2' }]), updateMany: jest.fn(() => Promise.resolve({})) },
    refreshToken: { updateMany: jest.fn(() => Promise.resolve({})) },
    $transaction: jest.fn((operations) => Promise.all(operations)),
  };
  const token = signAccessToken({ merchantId: merchant.merchantId, deviceId: DEVICE_ID, sessionId: 'session-1' });
  expect((await request(appFor(prisma)).post('/auth/logout-all').set('Authorization', `Bearer ${token}`)).status).toBe(204);
  expect(prisma.authSession.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: { merchantId: merchant.id } }));
  expect(prisma.refreshToken.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: { sessionId: { in: ['session-1', 'session-2'] } } }));
});
