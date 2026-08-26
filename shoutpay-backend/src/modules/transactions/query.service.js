"use strict";

const repository = require('./query.repository');

function toPublicTransaction(txn) {
  return {
    transactionUuid: txn.transactionUuid,
    amount: Number(txn.amount),
    currency: txn.currency,
    senderName: txn.senderName,
    senderVpa: txn.senderVpa,
    upiReference: txn.upiReference,
    upiApp: txn.upiApp,
    transactionTime: txn.transactionTime,
    status: txn.status
  };
}

async function getTransactions(prisma, merchantId, query) {
  const merchant = await repository.findMerchantByMerchantId(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }

  const page = Number(query.page) || 1;
  const limit = Number(query.limit) || 20;

  const { transactions, total } = await repository.getTransactions(prisma, merchant.id, {
    page,
    limit,
    startDate: query.startDate,
    endDate: query.endDate
  });

  const totalPages = Math.ceil(total / limit) || 0;

  return {
    transactions: transactions.map(toPublicTransaction),
    pagination: {
      page,
      limit,
      total,
      totalPages
    }
  };
}

async function getSummary(prisma, merchantId) {
  const merchant = await repository.findMerchantByMerchantId(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }

  const summary = await repository.getSummary(prisma, merchant.id);
  return { summary };
}

module.exports = { getTransactions, getSummary, toPublicTransaction };
