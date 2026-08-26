"use strict";

/**
 * Query repository — only database operations for reading cloud transactions.
 * Always filters by authenticated merchantId.
 */

async function findMerchantByMerchantId(prisma, merchantId) {
  return prisma.merchant.findUnique({ where: { merchantId } });
}

async function getTransactions(prisma, merchantInternalId, { page, limit, startDate, endDate }) {
  const where = {
    merchantId: merchantInternalId,
    status: 'SUCCESS'
  };
  if (startDate || endDate) {
    where.transactionTime = {};
    if (startDate) where.transactionTime.gte = new Date(startDate);
    if (endDate) where.transactionTime.lte = new Date(endDate);
  }

  const skip = (page - 1) * limit;
  const [transactions, total] = await Promise.all([
    prisma.transaction.findMany({
      where,
      orderBy: { transactionTime: 'desc' },
      skip,
      take: limit
    }),
    prisma.transaction.count({ where })
  ]);

  return { transactions, total };
}

async function getSummary(prisma, merchantInternalId) {
  // Backend timezone explicit: UTC start of today
  const now = new Date();
  const startOfTodayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0, 0));
  const endOfTodayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 23, 59, 59, 999));

  const whereToday = {
    merchantId: merchantInternalId,
    status: 'SUCCESS',
    transactionTime: {
      gte: startOfTodayUtc,
      lte: endOfTodayUtc
    }
  };

  const todayTransactions = await prisma.transaction.findMany({
    where: whereToday,
    select: { amount: true }
  });

  const todayCount = todayTransactions.length;
  const todayTotal = todayTransactions.reduce((sum, t) => sum + Number(t.amount), 0);
  const averageTransaction = todayCount > 0 ? todayTotal / todayCount : 0;
  const largestTransaction = todayTransactions.length > 0 ? Math.max(...todayTransactions.map(t => Number(t.amount))) : 0;

  return {
    todayTotal,
    todayCount,
    averageTransaction,
    largestTransaction
  };
}

module.exports = { findMerchantByMerchantId, getTransactions, getSummary };
