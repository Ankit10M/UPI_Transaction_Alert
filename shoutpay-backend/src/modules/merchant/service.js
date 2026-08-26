"use strict";

const repository = require('./repository');

/**
 * Service layer — validates business rules, protects immutable fields,
 * coordinates repository calls. Never accepts merchantId from client payload.
 */

async function getMerchantProfile(prisma, merchantId) {
  const merchant = await repository.findMerchantById(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }
  return merchant;
}

async function updateMerchantProfile(prisma, merchantId, data) {
  // Business rule: at least one allowed field must be present (validated upstream),
  // and only allowed fields reach this layer. Double-guard immutable fields.
  const allowed = {};
  if (typeof data.ownerName === 'string') allowed.ownerName = data.ownerName;
  if (typeof data.shopName === 'string') allowed.shopName = data.shopName;

  if (Object.keys(allowed).length === 0) {
    const err = new Error('No updatable fields provided');
    err.status = 400;
    err.code = 'VALIDATION_ERROR';
    throw err;
  }

  const existing = await repository.findMerchantById(prisma, merchantId);
  if (!existing) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }

  try {
    const updated = await repository.updateMerchantProfile(prisma, merchantId, allowed);
    return updated;
  } catch (error) {
    // Prisma P2025 = record not found race condition
    if (error.code === 'P2025') {
      error.status = 404;
      error.code = 'MERCHANT_NOT_FOUND';
    }
    throw error;
  }
}

module.exports = { getMerchantProfile, updateMerchantProfile };
