"use strict";

process.env.JWT_SECRET = 'transaction-test-secret';
process.env.AUTH_ACCESS_TOKEN_TTL = '15m';
process.env.AUTH_REFRESH_TOKEN_TTL = '30d';
process.env.NODE_ENV = 'test';

const express = require('express');
const request = require('supertest');
const { signAccessToken } = require('../../../auth/jwt');
const { transactionsRouterFor } = require('../router');

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

function tokenFor(merchantId, deviceId, sessionId = 'session-1') {
  return signAccessToken({ merchantId, deviceId, sessionId });
}

function appFor(prisma) {
  const app = express();
  app.use(express.json());
  app.use('/api/transactions', transactionsRouterFor(prisma));
  app.use((err, _req, res, _next) => {
    const status = Number.isInteger(err.status) ? err.status : 500;
    const code = err.code || (status >= 500 ? 'INTERNAL_SERVER_ERROR' : 'REQUEST_FAILED');
    return res.status(status).json({ error: { code, message: err.message } });
  });
  return app;
}

function validTransaction(overrides = {}) {
  return {
    transactionUuid: '550e8400-e29b-41d4-a716-446655440001',
    deviceId: DEVICE_ID_A,
    amount: 500,
    senderName: 'Rahul',
    senderVpa: 'rahul@upi',
    upiReference: 'REF123',
    upiApp: 'PhonePe',
    transactionTime: '2026-01-15T10:30:00.000Z',
    ...overrides
  };
}

describe('Transaction Sync Module', () => {
  test('Authentication - missing JWT returns 401', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn() },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const res = await request(app).post('/api/transactions/sync').send({ transactions: [validTransaction()] });
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHORIZED');
  });

  test('Authentication - invalid JWT returns 401', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn() },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', 'Bearer invalid.token.here')
      .send({ transactions: [validTransaction()] });
    expect(res.status).toBe(401);
  });

  test('Validation - empty transactions array returns 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [] });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('Validation - invalid UUID returns 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [validTransaction({ transactionUuid: 'not-a-uuid' })] });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('Validation - forbidden fields (merchantId, firebaseUid, status, id) returns 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    for (const payload of [
      validTransaction({ merchantId: 'SP-FAKE' }),
      validTransaction({ firebaseUid: 'fake-uid' }),
      validTransaction({ status: 'SUCCESS' }),
      validTransaction({ id: 'fake-id' }),
      validTransaction({ createdAt: new Date().toISOString() })
    ]) {
      const res = await request(app)
        .post('/api/transactions/sync')
        .set('Authorization', `Bearer ${token}`)
        .send({ transactions: [payload] });
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
    }
  });

  test('Validation - missing required fields returns 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const noAmount = { ...validTransaction() }; delete noAmount.amount;
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [noAmount] });
    expect(res.status).toBe(400);
  });

  test('Validation - amount must be positive', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [validTransaction({ amount: -100 })] });
    expect(res.status).toBe(400);
  });

  test('Validation - max 100 transactions', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const many = Array.from({ length: 101 }, (_, i) => validTransaction({ transactionUuid: `550e8400-e29b-41d4-a716-44665544${String(i).padStart(4,'0')}`.slice(0,36) }));
    // generate valid uuids for test - use known uuids
    const validMany = Array.from({ length: 101 }, () => validTransaction({ transactionUuid: '550e8400-e29b-41d4-a716-446655440001' }));
    // Actually use same uuid to test max count - validation should fail on length before uuid uniqueness
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: validMany });
    expect(res.status).toBe(400);
  });

  test('Transaction sync - successful creation', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async ({ where }) => where.merchantId === merchantA.merchantId ? merchantA : null) },
      transaction: {
        findUnique: jest.fn(async () => null),
        create: jest.fn(async ({ data }) => ({ id: 'txn-db-1', ...data }))
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const txn = validTransaction();
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [txn] });
    expect(res.status).toBe(200);
    expect(res.body.created).toEqual([txn.transactionUuid]);
    expect(res.body.duplicates).toEqual([]);
    expect(prisma.transaction.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          transactionUuid: txn.transactionUuid,
          merchantId: merchantA.id,
          deviceId: txn.deviceId,
          amount: txn.amount,
          senderName: txn.senderName,
          currency: 'INR',
          status: 'SUCCESS'
        })
      })
    );
    // Verify merchantId from JWT is used, not from client
    expect(prisma.transaction.create.mock.calls[0][0].data.merchantId).toBe(merchantA.id);
  });

  test('Transaction sync - JWT merchantId is used (client merchantId rejected)', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async ({ where }) => where.merchantId === merchantA.merchantId ? merchantA : null) },
      transaction: {
        findUnique: jest.fn(async () => null),
        create: jest.fn(async ({ data }) => ({ id: 'txn-db-1', ...data }))
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    // Attempt to send merchantId in payload should be rejected by validation
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [{ ...validTransaction(), merchantId: merchantB.merchantId }] });
    expect(res.status).toBe(400);
  });

  test('Transaction sync - duplicate upload returns duplicate', async () => {
    const existing = { transactionUuid: '550e8400-e29b-41d4-a716-446655440001' };
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findUnique: jest.fn(async ({ where }) => where.transactionUuid === existing.transactionUuid ? existing : null),
        create: jest.fn()
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const txn = validTransaction({ transactionUuid: existing.transactionUuid });
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [txn] });
    expect(res.status).toBe(200);
    expect(res.body.created).toEqual([]);
    expect(res.body.duplicates).toEqual([existing.transactionUuid]);
    expect(prisma.transaction.create).not.toHaveBeenCalled();
  });

  test('Idempotency - same UUID uploaded twice second is duplicate (no duplicate rows)', async () => {
    const store = new Map();
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findUnique: jest.fn(async ({ where }) => store.get(where.transactionUuid) || null),
        create: jest.fn(async ({ data }) => {
          if (store.has(data.transactionUuid)) {
            const err = new Error('Unique constraint');
            err.code = 'P2002';
            err.meta = { target: ['transactionUuid'] };
            throw err;
          }
          store.set(data.transactionUuid, data);
          return { id: 'txn-db-1', ...data };
        })
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const txn = validTransaction();
    const first = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${token}`).send({ transactions: [txn] });
    expect(first.status).toBe(200);
    expect(first.body.created).toEqual([txn.transactionUuid]);
    const second = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${token}`).send({ transactions: [txn] });
    expect(second.status).toBe(200);
    expect(second.body.created).toEqual([]);
    expect(second.body.duplicates).toEqual([txn.transactionUuid]);
    expect(store.size).toBe(1);
  });

  test('Merchant isolation - Merchant A cannot affect Merchant B, duplicate is global but merchantId from JWT ensures isolation', async () => {
    const store = new Map();
    const prisma = {
      merchant: {
        findUnique: jest.fn(async ({ where }) => {
          if (where.merchantId === merchantA.merchantId) return merchantA;
          if (where.merchantId === merchantB.merchantId) return merchantB;
          return null;
        })
      },
      transaction: {
        findUnique: jest.fn(async ({ where }) => store.get(where.transactionUuid) || null),
        create: jest.fn(async ({ data }) => {
          store.set(data.transactionUuid, data);
          return { id: 'txn-db-1', ...data };
        }),
        findMany: jest.fn()
      }
    };
    const app = appFor(prisma);
    const tokenA = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const tokenB = tokenFor(merchantB.merchantId, DEVICE_ID_B);
    const txnA = validTransaction({ transactionUuid: '550e8400-e29b-41d4-a716-446655440010' });
    const resA = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${tokenA}`).send({ transactions: [txnA] });
    expect(resA.status).toBe(200);
    // Verify stored merchantId is from JWT (merchantA.id)
    expect(prisma.transaction.create).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ merchantId: merchantA.id }) }));
    // Merchant B uploads different UUID
    const txnB = validTransaction({ transactionUuid: '550e8400-e29b-41d4-a716-446655440020' });
    const resB = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${tokenB}`).send({ transactions: [txnB] });
    expect(resB.status).toBe(200);
    expect(resB.body.created).toEqual([txnB.transactionUuid]);
    // Ensure B's transaction stored with B's merchantId
    expect(prisma.transaction.create).toHaveBeenLastCalledWith(expect.objectContaining({ data: expect.objectContaining({ merchantId: merchantB.id }) }));
  });

  test('DeviceId is stored but does not decide ownership (merchant isolation via JWT)', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findUnique: jest.fn(async () => null),
        create: jest.fn(async ({ data }) => ({ id: 'txn-db-1', ...data }))
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const txn = validTransaction({ deviceId: 'foreign-device-id-999' });
    const res = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${token}`).send({ transactions: [txn] });
    expect(res.status).toBe(200);
    expect(prisma.transaction.create).toHaveBeenCalledWith(expect.objectContaining({ data: expect.objectContaining({ deviceId: 'foreign-device-id-999', merchantId: merchantA.id }) }));
  });

  test('No firebaseUid exposure in response', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findUnique: jest.fn(async () => null),
        create: jest.fn(async ({ data }) => ({ id: 'txn-db-1', ...data }))
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${token}`).send({ transactions: [validTransaction()] });
    expect(res.status).toBe(200);
    const bodyStr = JSON.stringify(res.body);
    expect(bodyStr).not.toMatch(/firebaseUid/i);
    expect(bodyStr).not.toMatch(/merchantId.*SP-/i) // should not leak other merchantIds, but own created is returned as uuid only
    expect(res.body.created).toBeDefined();
    expect(Array.isArray(res.body.created)).toBe(true);
  });

  test('Database failure handling returns 500', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => { throw Object.assign(new Error('DB down'), { code: 'P1001' }); }) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app).post('/api/transactions/sync').set('Authorization', `Bearer ${token}`).send({ transactions: [validTransaction()] });
    expect(res.status).toBe(500);
    expect(res.body.error.code).toBeDefined();
  });

  test('Validation - transactionTime must be ISO date', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findUnique: jest.fn(), create: jest.fn() }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId, DEVICE_ID_A);
    const res = await request(app)
      .post('/api/transactions/sync')
      .set('Authorization', `Bearer ${token}`)
      .send({ transactions: [validTransaction({ transactionTime: 'not-a-date' })] });
    expect(res.status).toBe(400);
  });
});
