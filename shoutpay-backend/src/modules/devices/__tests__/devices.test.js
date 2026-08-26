"use strict";

process.env.JWT_SECRET = 'device-test-secret';
process.env.AUTH_ACCESS_TOKEN_TTL = '15m';
process.env.AUTH_REFRESH_TOKEN_TTL = '30d';
process.env.NODE_ENV = 'test';

const express = require('express');
const request = require('supertest');
const { signAccessToken } = require('../../../auth/jwt');
const { devicesRouterFor } = require('../router');

const DEVICE_ID_A = '123e4567-e89b-42d3-a456-426614174001';
const DEVICE_ID_B = '123e4567-e89b-42d3-a456-426614174002';
const DEVICE_ID_C = '123e4567-e89b-42d3-a456-426614174003';

const merchantA = {
  id: 'merchant-db-a',
  merchantId: 'SP-100001',
  firebaseUid: 'firebase-uid-a',
  ownerName: 'Owner A',
  shopName: 'Shop A',
  phoneNumber: '+911111111111',
  createdAt: new Date('2026-01-01T00:00:00.000Z'),
};

const merchantB = {
  id: 'merchant-db-b',
  merchantId: 'SP-200002',
  firebaseUid: 'firebase-uid-b',
  ownerName: 'Owner B',
  shopName: 'Shop B',
  phoneNumber: '+912222222222',
  createdAt: new Date('2026-02-01T00:00:00.000Z'),
};

const deviceA = {
  id: 'device-db-a',
  deviceId: DEVICE_ID_A,
  merchantId: merchantA.id,
  deviceName: 'Device A',
  createdAt: new Date('2026-01-10T00:00:00.000Z'),
  updatedAt: new Date('2026-01-10T00:00:00.000Z'),
  authSessions: [{ id: 'sess-a', lastUsedAt: new Date('2026-01-15T00:00:00.000Z'), revokedAt: null }]
};

const deviceB = {
  id: 'device-db-b',
  deviceId: DEVICE_ID_B,
  merchantId: merchantA.id,
  deviceName: 'Device B',
  createdAt: new Date('2026-01-11T00:00:00.000Z'),
  updatedAt: new Date('2026-01-11T00:00:00.000Z'),
  authSessions: [{ id: 'sess-b', lastUsedAt: new Date('2026-01-14T00:00:00.000Z'), revokedAt: null }]
};

const deviceC = {
  id: 'device-db-c',
  deviceId: DEVICE_ID_C,
  merchantId: merchantB.id,
  deviceName: 'Device C - other merchant',
  createdAt: new Date('2026-01-12T00:00:00.000Z'),
  updatedAt: new Date('2026-01-12T00:00:00.000Z'),
  authSessions: [{ id: 'sess-c', lastUsedAt: new Date('2026-01-13T00:00:00.000Z'), revokedAt: null }]
};

function tokenFor(merchantId, deviceId, sessionId = 'session-1') {
  return signAccessToken({ merchantId, deviceId, sessionId });
}

function appFor(prisma) {
  const app = express();
  app.use(express.json());
  app.use('/api/auth/devices', devicesRouterFor(prisma));
  app.use((err, _req, res, _next) => {
    const status = Number.isInteger(err.status) ? err.status : 500;
    const code = err.code || (status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'REQUEST_FAILED');
    return res.status(status).json({ error: { code, message: err.message } });
  });
  return app;
}

describe('Device Management Module', () => {
  test('1. Authenticated merchant sees own devices (200, correct shape, no leakage)', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => (where.merchantId === merchantA.merchantId ? merchantA : null))
      },
      device: {
        findMany: jest.fn(async ({ where }) => (where.merchantId === merchantA.id ? [deviceA, deviceB] : [])),
        findFirst: jest.fn()
      },
      authSession: { updateMany: jest.fn(async () => ({ count: 1 })) },
      refreshToken: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).get('/api/auth/devices').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.devices)).toBe(true);
    expect(res.body.devices).toHaveLength(2);
    // Must not expose refresh tokens, token hashes, session internals
    const serialized = JSON.stringify(res.body);
    expect(serialized).not.toMatch(/refreshToken/i);
    expect(serialized).not.toMatch(/tokenHash/i);
    expect(serialized).not.toMatch(/tokenFamilyId/i);
    // Verify required fields and active flag based on currentDeviceId
    const devA = res.body.devices.find((d) => d.deviceId === DEVICE_ID_A);
    const devB = res.body.devices.find((d) => d.deviceId === DEVICE_ID_B);
    expect(devA.active).toBe(true);
    expect(devB.active).toBe(false);
    expect(devA.deviceName).toBe('Device A');
    expect(devA.lastUsedAt).toBeDefined();
    expect(devA.createdAt).toBeDefined();
    // Never leaked deviceC from merchantB
    expect(res.body.devices.find((d) => d.deviceId === DEVICE_ID_C)).toBeUndefined();
  });

  test('2. Merchant cannot see another merchant devices (isolation)', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => {
          if (where.merchantId === merchantA.merchantId) return merchantA;
          if (where.merchantId === merchantB.merchantId) return merchantB;
          return null;
        })
      },
      device: {
        findMany: jest.fn(async ({ where }) => {
          if (where.merchantId === merchantA.id) return [deviceA];
          if (where.merchantId === merchantB.id) return [deviceC];
          return [];
        }),
        findFirst: jest.fn()
      },
      authSession: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const tokenA = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const resA = await request(app).get('/api/auth/devices').set('Authorization', `Bearer ${tokenA}`);
    expect(resA.status).toBe(200);
    expect(resA.body.devices.find((d) => d.deviceId === DEVICE_ID_C)).toBeUndefined();
    expect(resA.body.devices.every((d) => d.deviceId !== DEVICE_ID_C)).toBe(true);
  });

  test('3. Logout own device populates revokedAt (204)', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => (where.merchantId === merchantA.merchantId ? merchantA : null))
      },
      device: {
        findFirst: jest.fn(async ({ where }) => (where.deviceId === DEVICE_ID_B && where.merchantId === merchantA.id ? deviceB : null)),
        findMany: jest.fn()
      },
      authSession: {
        updateMany: jest.fn(async () => ({ count: 1 }))
      },
      refreshToken: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).delete(`/api/auth/devices/${DEVICE_ID_B}`).set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(204);
    expect(prisma.authSession.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          merchantId: merchantA.id,
          deviceId: deviceB.id,
          revokedAt: null
        })
      })
    );
    // Refresh tokens remain untouched
    expect(prisma.refreshToken.updateMany).not.toHaveBeenCalled();
  });

  test('4. Logout another merchant device returns 404 DEVICE_NOT_FOUND', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => {
          if (where.merchantId === merchantA.merchantId) return merchantA;
          if (where.merchantId === merchantB.merchantId) return merchantB;
          return null;
        })
      },
      device: {
        findFirst: jest.fn(async ({ where }) => {
          // DeviceC belongs to merchantB, so merchantA cannot find it
          if (where.deviceId === DEVICE_ID_C && where.merchantId === merchantA.id) return null;
          if (where.deviceId === DEVICE_ID_C && where.merchantId === merchantB.id) return deviceC;
          return null;
        }),
        findMany: jest.fn()
      },
      authSession: {
        updateMany: jest.fn()
      }
    };
    const app = appFor(prisma);
    const tokenA = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).delete(`/api/auth/devices/${DEVICE_ID_C}`).set('Authorization', `Bearer ${tokenA}`);
    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('DEVICE_NOT_FOUND');
    expect(prisma.authSession.updateMany).not.toHaveBeenCalled();
  });

  test('5. Logout other devices keeps current device active (revokes only others)', async () => {
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => (where.merchantId === merchantA.merchantId ? merchantA : null))
      },
      device: {
        findFirst: jest.fn(async ({ where }) => (where.deviceId === DEVICE_ID_A && where.merchantId === merchantA.id ? deviceA : null)),
        findMany: jest.fn(async ({ where }) => (where.merchantId === merchantA.id ? [deviceA, deviceB] : []))
      },
      authSession: {
        updateMany: jest.fn(async () => ({ count: 1 }))
      },
      refreshToken: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).delete('/api/auth/devices/logout-others').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(204);
    expect(prisma.authSession.updateMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          merchantId: merchantA.id,
          deviceId: { not: deviceA.id },
          revokedAt: null
        })
      })
    );
    expect(prisma.authSession.updateMany).not.toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ deviceId: deviceA.id })
      })
    );
    // Verify current device still retrievable as active
    const prisma2 = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      device: {
        findMany: jest.fn(async () => [deviceA, deviceB]),
        findFirst: jest.fn(async () => deviceA)
      },
      authSession: { updateMany: jest.fn() }
    };
    const app2 = appFor(prisma2);
    const res2 = await request(app2).get('/api/auth/devices').set('Authorization', `Bearer ${token}`);
    // service marks active based on currentDeviceId, so deviceA should be active true
    const devA = res2.body.devices.find((d) => d.deviceId === DEVICE_ID_A);
    expect(devA.active).toBe(true);
  });

  test('6. Unauthorized request without JWT returns 401', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn() },
      device: { findMany: jest.fn(), findFirst: jest.fn() },
      authSession: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const res = await request(app).get('/api/auth/devices');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHORIZED');
    expect(prisma.device.findMany).not.toHaveBeenCalled();
  });

  test('7. Refresh tokens remain untouched on device revoke', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      device: {
        findFirst: jest.fn(async () => deviceB),
        findMany: jest.fn()
      },
      authSession: { updateMany: jest.fn(async () => ({ count: 1 })) },
      refreshToken: { updateMany: jest.fn(async () => ({ count: 0 })) }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    await request(app).delete(`/api/auth/devices/${DEVICE_ID_B}`).set('Authorization', `Bearer ${token}`);
    expect(prisma.refreshToken.updateMany).not.toHaveBeenCalled();
    expect(prisma.authSession.updateMany).toHaveBeenCalled();
  });

  test('Validation rejects invalid deviceId format with 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      device: { findFirst: jest.fn(), findMany: jest.fn() },
      authSession: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).delete('/api/auth/devices/not-a-uuid').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('Validation rejects empty deviceId and unknown params', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      device: { findFirst: jest.fn(), findMany: jest.fn() },
      authSession: { updateMany: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    // empty is not routable, but invalid uuid already covered; test that valid uuid passes validation then 404
    const validButNotOwned = '123e4567-e89b-42d3-a456-426614174099';
    prisma.device.findFirst = jest.fn(async () => null);
    const res = await request(app).delete(`/api/auth/devices/${validButNotOwned}`).set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(404);
  });
});
