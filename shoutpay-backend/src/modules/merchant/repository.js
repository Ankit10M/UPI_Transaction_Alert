"use strict";

/**
 * Merchant repository — only database operations, no business logic.
 * All identity is supplied externally (JWT.sub); repository never trusts client payload.
 */

async function findMerchantById(prisma, merchantId) {
  return prisma.merchant.findUnique({ where: { merchantId } });
}

async function updateMerchantProfile(prisma, merchantId, data) {
  return prisma.merchant.update({ where: { merchantId }, data });
}

module.exports = { findMerchantById, updateMerchantProfile };
