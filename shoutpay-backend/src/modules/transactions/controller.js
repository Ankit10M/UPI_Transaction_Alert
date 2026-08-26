"use strict";

const service = require('./service');

function sync(prisma) {
  return async (req, res, next) => {
    try {
      const { transactions } = req.validatedBody || req.body;
      const result = await service.syncTransactions(prisma, req.auth.merchantId, transactions);
      return res.status(200).json(result);
    } catch (error) {
      return next(error);
    }
  };
}

module.exports = { sync };
