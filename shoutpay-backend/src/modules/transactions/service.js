"use strict";

const repository = require('./repository');

/**
 * Service layer for transaction sync.
 * - JWT merchantId is source of truth
 * - Idempotency via transactionUuid unique
 * - DeviceId from request stored but does not decide ownership
 */

async function syncTransactions(prisma, merchantId, transactions) {
  const merchant = await repository.findMerchantByMerchantId(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }

  const created = [];
  const duplicates = [];

  for (const txn of transactions) {
    const existing = await repository.findByTransactionUuid(prisma, txn.transactionUuid);
    if (existing) {
      duplicates.push(txn.transactionUuid);
      continue;
    }

    try {
      const data = {
        transactionUuid: txn.transactionUuid,
        merchantId: merchant.id,
        deviceId: txn.deviceId,
        amount: txn.amount,
        currency: 'INR',
        senderName: txn.senderName,
        senderVpa: txn.senderVpa || null,
        upiReference: txn.upiReference || null,
        upiApp: txn.upiApp || null,
        transactionTime: new Date(txn.transactionTime),
        status: 'SUCCESS'
      };
      await repository.createTransaction(prisma, data);
      created.push(txn.transactionUuid);
    } catch (error) {
      // Handle unique constraint violation race (P2002)
      if (error.code === 'P2002' && error.meta && error.meta.target && error.meta.target.includes('transactionUuid')) {
        duplicates.push(txn.transactionUuid);
      } else {
        throw error;
      }
    }
  }

  return { created, duplicates };
}

module.exports = { syncTransactions };
