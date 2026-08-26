"use strict";

const service = require('./service');

function toPublicMerchant(merchant) {
  return {
    merchantId: merchant.merchantId,
    ownerName: merchant.ownerName,
    shopName: merchant.shopName,
    phoneNumber: merchant.phoneNumber,
    createdAt: merchant.createdAt,
  };
}

function getProfile(prisma) {
  return async (req, res, next) => {
    try {
      const merchant = await service.getMerchantProfile(prisma, req.auth.merchantId);
      return res.json({ merchant: toPublicMerchant(merchant) });
    } catch (error) {
      return next(error);
    }
  };
}

function updateProfile(prisma) {
  return async (req, res, next) => {
    try {
      const data = req.validatedBody || req.body;
      const merchant = await service.updateMerchantProfile(prisma, req.auth.merchantId, data);
      return res.json({ merchant: toPublicMerchant(merchant) });
    } catch (error) {
      return next(error);
    }
  };
}

module.exports = { toPublicMerchant, getProfile, updateProfile };
