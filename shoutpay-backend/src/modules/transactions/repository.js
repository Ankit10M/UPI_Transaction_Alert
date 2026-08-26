"use strict";

/**
 * Transaction repository — only database operations, no business logic.
 */

async function findMerchantByMerchantId(prisma, merchantId) {
  return prisma.merchant.findUnique({ where: { merchantId } });
}

async function findByTransactionUuid(prisma, transactionUuid) {
  return prisma.transaction.findUnique({ where: { transactionUuid } });
}

async function createTransaction(prisma, data) {
  return prisma.transaction.create({ data });
}

async function findTransactionsByMerchantId(prisma, merchantId) {
  const merchant = await prisma.merchant.findUnique({ where: { merchantId } });
  if (!merchant) return [];
  return prisma.transaction.findMany({ where: { merchantId: merchant.id } });
}

module.exports = {
  findMerchantByMerchantId,
  findByTransactionUuid,
  createTransaction,
  findTransactionsByMerchantId
};
