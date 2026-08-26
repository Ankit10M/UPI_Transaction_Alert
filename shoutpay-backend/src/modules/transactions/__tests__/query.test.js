"use strict";

process.env.JWT_SECRET = 'query-test-secret';
process.env.AUTH_ACCESS_TOKEN_TTL = '15m';
process.env.AUTH_REFRESH_TOKEN_TTL = '30d';
process.env.NODE_ENV = 'test';

const express = require('express');
const request = require('supertest');
const { signAccessToken } = require('../../../auth/jwt');
const { transactionsRouterFor } = require('../router');

const DEVICE_ID_A = '123e4567-e89b-42d3-a456-426614174001';

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

function tokenFor(merchantId, deviceId = DEVICE_ID_A, sessionId = 'session-1') {
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

function makeTxn(overrides = {}) {
  return {
    id: `id-${Math.random().toString(36).slice(2)}`,
    transactionUuid: '550e8400-e29b-41d4-a716-446655440001',
    merchantId: merchantA.id,
    deviceId: DEVICE_ID_A,
    amount: 500,
    currency: 'INR',
    senderName: 'Rahul',
    senderVpa: 'rahul@upi',
    upiReference: 'REF123',
    upiApp: 'PhonePe',
    transactionTime: new Date('2026-03-10T10:00:00.000Z'),
    status: 'SUCCESS',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides
  };
}

describe('Transaction Query Module', () => {
  test('1. Authenticated merchant gets own transactions', async () => {
    const txn = makeTxn();
    const prisma = {
      merchant: { findUnique: jest.fn(async ({ where }) => where.merchantId === merchantA.merchantId ? merchantA : null) },
      transaction: {
        findMany: jest.fn(async () => [txn]),
        count: jest.fn(async () => 1)
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.transactions).toHaveLength(1);
    expect(res.body.transactions[0].transactionUuid).toBe(txn.transactionUuid);
    expect(res.body.pagination.total).toBe(1);
    expect(res.body.pagination.page).toBe(1);
  });

  test('2. Merchant isolation — A cannot see B transactions', async () => {
    const txnB = makeTxn({ transactionUuid: '550e8400-e29b-41d4-a716-446655440002', merchantId: merchantB.id });
    const prisma = {
      merchant: { findUnique: jest.fn(async ({ where }) => where.merchantId === merchantA.merchantId ? merchantA : null) },
      transaction: {
        findMany: jest.fn(async ({ where }) => {
          // Ensure query filters by merchantA.id, not B
          expect(where.merchantId).toBe(merchantA.id);
          expect(where.merchantId).not.toBe(merchantB.id);
          return [];
        }),
        count: jest.fn(async () => 0)
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.transactions).toEqual([]);
    // Verify B's transaction not leaked
    expect(res.body.transactions.find(t => t.transactionUuid === txnB.transactionUuid)).toBeUndefined();
  });

  test('3. Pagination works', async () => {
    const allTxns = Array.from({ length: 5 }, (_, i) => makeTxn({ transactionUuid: `550e8400-e29b-41d4-a716-44665544${String(i).padStart(4,'0')}` }));
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findMany: jest.fn(async ({ skip, take }) => allTxns.slice(skip, skip + take)),
        count: jest.fn(async () => allTxns.length)
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions?page=2&limit=2').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.transactions).toHaveLength(2);
    expect(res.body.pagination.page).toBe(2);
    expect(res.body.pagination.limit).toBe(2);
    expect(res.body.pagination.total).toBe(5);
    expect(res.body.pagination.totalPages).toBe(3);
  });

  test('4. Limit maximum enforced (max 100)', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findMany: jest.fn(async () => []), count: jest.fn(async () => 0) }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions?limit=200').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('4b. Limit minimum enforced', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findMany: jest.fn(async () => []), count: jest.fn(async () => 0) }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions?limit=0').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(400);
  });

  test('5. Date filtering works', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findMany: jest.fn(async ({ where }) => {
          expect(where.transactionTime.gte).toBeDefined();
          expect(where.transactionTime.lte).toBeDefined();
          return [makeTxn()];
        }),
        count: jest.fn(async () => 1)
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app)
      .get('/api/transactions?startDate=2026-03-01T00:00:00.000Z&endDate=2026-03-31T23:59:59.000Z')
      .set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(prisma.transaction.findMany).toHaveBeenCalled();
  });

  test('5b. Invalid date returns 400', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findMany: jest.fn(async () => []), count: jest.fn(async () => 0) }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions?startDate=not-a-date').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(400);
  });

  test('6. Empty result returns []', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findMany: jest.fn(async () => []),
        count: jest.fn(async () => 0)
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.transactions).toEqual([]);
    expect(res.body.pagination.total).toBe(0);
    expect(res.body.pagination.totalPages).toBe(0);
  });

  test('7. Summary calculation correct (todayTotal, count, avg, largest) - SUCCESS only', async () => {
    const now = new Date();
    // Create 3 transactions today
    const todayTxns = [
      makeTxn({ amount: 500, transactionTime: now, status: 'SUCCESS' }),
      makeTxn({ amount: 1000, transactionTime: now, status: 'SUCCESS' }),
      makeTxn({ amount: 1500, transactionTime: now, status: 'SUCCESS' }),
    ];
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findMany: jest.fn(async ({ where }) => {
          // should filter SUCCESS and today range
          expect(where.status).toBe('SUCCESS');
          expect(where.transactionTime.gte).toBeDefined();
          return todayTxns;
        })
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions/summary').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.summary.todayTotal).toBe(3000);
    expect(res.body.summary.todayCount).toBe(3);
    expect(res.body.summary.averageTransaction).toBeCloseTo(1000);
    expect(res.body.summary.largestTransaction).toBe(1500);
  });

  test('8. Failed transaction excluded from summary', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findMany: jest.fn(async ({ where }) => {
          expect(where.status).toBe('SUCCESS');
          return [];
        })
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions/summary').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.summary.todayCount).toBe(0);
    expect(res.body.summary.todayTotal).toBe(0);
  });

  test('9. Unauthorized request rejected', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn() },
      transaction: { findMany: jest.fn(), count: jest.fn() }
    };
    const app = appFor(prisma);
    const res = await request(app).get('/api/transactions');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHORIZED');
  });

  test('10. Internal fields never exposed', async () => {
    const txn = {
      ...makeTxn(),
      id: 'internal-id-123',
      merchantId: merchantA.id,
      createdAt: new Date(),
      updatedAt: new Date(),
      deletedAt: null
    };
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: {
        findMany: jest.fn(async () => [txn]),
        count: jest.fn(async () => 1)
      }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    const bodyStr = JSON.stringify(res.body);
    expect(bodyStr).not.toMatch(/"id"/);
    expect(bodyStr).not.toMatch(/merchantId/);
    expect(bodyStr).not.toMatch(/firebaseUid/);
    // Only allowed fields should be present
    const returned = res.body.transactions[0];
    expect(returned.transactionUuid).toBeDefined();
    expect(returned.amount).toBeDefined();
    expect(returned.currency).toBeDefined();
    expect(returned.senderName).toBeDefined();
    expect(returned.transactionTime).toBeDefined();
    expect(returned.status).toBeDefined();
  });

  test('Validation - unknown fields rejected', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findMany: jest.fn(async () => []), count: jest.fn(async () => 0) }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions?unknownField=123').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('Validation - negative page rejected', async () => {
    const prisma = {
      merchant: { findUnique: jest.fn(async () => merchantA) },
      transaction: { findMany: jest.fn(async () => []), count: jest.fn(async () => 0) }
    };
    const app = appFor(prisma);
    const token = tokenFor(merchantA.merchantId);
    const res = await request(app).get('/api/transactions?page=-1').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(400);
  });
});
