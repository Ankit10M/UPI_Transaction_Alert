"use strict";

process.env.JWT_SECRET = 'merchant-test-secret';
process.env.AUTH_ACCESS_TOKEN_TTL = '15m';
process.env.AUTH_REFRESH_TOKEN_TTL = '30d';
process.env.NODE_ENV = 'test';

const express = require('express');
const request = require('supertest');
const { signAccessToken } = require('../../../auth/jwt');
const { merchantRouterFor } = require('../router');

const DEVICE_ID_A = '123e4567-e89b-42d3-a456-426614174001';
const DEVICE_ID_B = '123e4567-e89b-42d3-a456-426614174002';

const merchantA = {
  id: 'merchant-db-a',
  merchantId: 'SP-100001',
  firebaseUid: 'firebase-uid-a',
  ownerName: 'Owner A',
  shopName: 'Shop A',
  phoneNumber: '+911111111111',
  createdAt: new Date('2026-01-01T00:00:00.000Z'),
  updatedAt: new Date('2026-01-01T00:00:00.000Z'),
};

const merchantB = {
  id: 'merchant-db-b',
  merchantId: 'SP-200002',
  firebaseUid: 'firebase-uid-b',
  ownerName: 'Owner B',
  shopName: 'Shop B',
  phoneNumber: '+912222222222',
  createdAt: new Date('2026-02-01T00:00:00.000Z'),
  updatedAt: new Date('2026-02-01T00:00:00.000Z'),
};

function tokenFor(merchantId, deviceId, sessionId = 'session-1') {
  return signAccessToken({ merchantId, deviceId, sessionId });
}

function appFor(prisma) {
  const app = express();
  app.use(express.json());
  app.use('/api/merchant', merchantRouterFor(prisma));
  app.use((err, _req, res, _next) => {
    const status = Number.isInteger(err.status) ? err.status : 500;
    const code = err.code || (status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'REQUEST_FAILED');
    return res.status(status).json({ error: { code, message: err.message } });
  });
  return app;
}

describe('Merchant Profile Module', () => {
  test('1. Successful profile fetch returns 200 with correct shape', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => (where.merchantId === merchantA.merchantId ? merchantA : null)),
        update: jest.fn(),
      },
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).get('/api/merchant/profile').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.merchant).toBeDefined();
    expect(res.body.merchant.merchantId).toBe(merchantA.merchantId);
    expect(res.body.merchant.ownerName).toBe(merchantA.ownerName);
    expect(res.body.merchant.shopName).toBe(merchantA.shopName);
    expect(res.body.merchant.phoneNumber).toBe(merchantA.phoneNumber);
    expect(res.body.merchant.createdAt).toBeDefined();
    // Must never expose internal id or firebaseUid
    expect(res.body.merchant.firebaseUid).toBeUndefined();
    expect(res.body.merchant.id).toBeUndefined();
    // Verify repo called with JWT identity only
    expect(prisma.merchant.findUnique).toHaveBeenCalledWith({ where: { merchantId: merchantA.merchantId } });
  });

  test('2. Unauthorized profile request without JWT returns 401', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(), update: jest.fn() },
    };
    const app = appFor(prisma);
    const res = await request(app).get('/api/merchant/profile');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHORIZED');
    expect(prisma.merchant.findUnique).not.toHaveBeenCalled();
  });

  test('3. Merchant isolation — Merchant A token cannot access Merchant B data', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => {
          if (where.merchantId === merchantA.merchantId) return merchantA;
          if (where.merchantId === merchantB.merchantId) return merchantB;
          return null;
        }),
        update: jest.fn(async ({ where, data }) => {
          if (where.merchantId === merchantA.merchantId) return { ...merchantA, ...data };
          return null;
        }),
      },
    };
    const app = appFor(prisma);
    const tokenA = tokenFor(merchantA.merchantId, DEVICE_ID_A);

    // GET: must return A's data even though B exists; never B's data
    const getRes = await request(app).get('/api/merchant/profile').set('Authorization', `Bearer ${tokenA}`);
    expect(getRes.status).toBe(200);
    expect(getRes.body.merchant.merchantId).toBe(merchantA.merchantId);
    expect(getRes.body.merchant.merchantId).not.toBe(merchantB.merchantId);
    expect(getRes.body.merchant.phoneNumber).toBe(merchantA.phoneNumber);

    // PATCH attempt with merchantId spoof in body must be rejected, not allow A to mutate B
    const spoofRes = await request(app)
      .patch('/api/merchant/profile')
      .set('Authorization', `Bearer ${tokenA}`)
      .send({ merchantId: merchantB.merchantId, ownerName: 'Hacked' });
    expect(spoofRes.status).toBe(400);
    // Ensure update was never called with spoofed id
    expect(prisma.merchant.update).not.toHaveBeenCalled();

    // Ensure even after spoof attempt, GET still returns A
    const getRes2 = await request(app).get('/api/merchant/profile').set('Authorization', `Bearer ${tokenA}`);
    expect(getRes2.body.merchant.merchantId).toBe(merchantA.merchantId);
  });

  test('4. Successful profile update changes ownerName/shopName', async () => {
    const updated = { ...merchantA, ownerName: 'New Owner', shopName: 'New Shop', createdAt: merchantA.createdAt };
    const prisma = {
      merchant: {
        findUnique: jest.fn(async () => merchantA),
        update: jest.fn(async ({ where, data }) => ({ ...merchantA, ...data })),
      },
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app)
      .patch('/api/merchant/profile')
      .set('Authorization', `Bearer ${token}`)
      .send({ ownerName: 'New Owner', shopName: 'New Shop' });
    expect(res.status).toBe(200);
    expect(res.body.merchant.ownerName).toBe('New Owner');
    expect(res.body.merchant.shopName).toBe('New Shop');
    expect(res.body.merchant.merchantId).toBe(merchantA.merchantId);
    expect(res.body.merchant.phoneNumber).toBe(merchantA.phoneNumber);
    expect(prisma.merchant.update).toHaveBeenCalledWith({
      where: { merchantId: merchantA.merchantId },
      data: { ownerName: 'New Owner', shopName: 'New Shop' },
    });
  });

  test('5. Immutable field protection — sending merchantId is rejected with 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(), update: jest.fn() },
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app)
      .patch('/api/merchant/profile')
      .set('Authorization', `Bearer ${token}`)
      .send({ merchantId: 'fake', ownerName: 'Hacked' });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    expect(prisma.merchant.update).not.toHaveBeenCalled();
  });

  test('5b. Immutable fields phoneNumber, firebaseUid, createdAt are rejected', async () => {
    const prisma = { merchant: { findUnique: jest.fn(), update: jest.fn() } };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    for (const payload of [
      { phoneNumber: '+919999999999' },
      { firebaseUid: 'evil' },
      { createdAt: new Date().toISOString() },
      { id: 'evil-id' },
      { status: 'SUSPENDED' },
    ]) {
      const res = await request(app).patch('/api/merchant/profile').set('Authorization', `Bearer ${token}`).send(payload);
      expect(res.status).toBe(400);
    }
    expect(prisma.merchant.update).not.toHaveBeenCalled();
  });

  test('5c. Empty body and unknown fields are rejected', async () => {
    const prisma = { merchant: { findUnique: jest.fn(), update: jest.fn() } };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    expect((await request(app).patch('/api/merchant/profile').set('Authorization', `Bearer ${token}`).send({})).status).toBe(400);
    expect((await request(app).patch('/api/merchant/profile').set('Authorization', `Bearer ${token}`).send({ unknownField: 'x' })).status).toBe(400);
  });

  test('6. Database failure handling returns production-safe error', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async () => { throw Object.assign(new Error('DB down'), { code: 'P1001' }); }),
        update: jest.fn(),
      },
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).get('/api/merchant/profile').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(500);
    expect(res.body.error).toBeDefined();
    expect(res.body.error.code).toBeDefined();
  });

  test('GET returns 404 when merchant does not exist', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => null), update: jest.fn() },
    };
    const app = appFor(prisma);
    const token = tokenFor('SP-999999', DEVICE_ID_A);
    const res = await request(app).get('/api/merchant/profile').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('MERCHANT_NOT_FOUND');
  });

  test('PATCH returns 404 when merchant does not exist', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => null), update: jest.fn() },
    };
    const app = appFor(prisma);
    const token = tokenFor('SP-999999', DEVICE_ID_A);
    const res = await request(app).patch('/api/merchant/profile').set('Authorization', `Bearer ${token}`).send({ ownerName: 'X' });
    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('MERCHANT_NOT_FOUND');
  });

  test('Unauthorized with invalid token returns 401', async () => {
    const prisma = { merchant: { findUnique: jest.fn(), update: jest.fn() } };
    const app = appFor(prisma);
    const res = await request(app).get('/api/merchant/profile').set('Authorization', 'Bearer invalid.token.here');
    expect(res.status).toBe(401);
  });
});
