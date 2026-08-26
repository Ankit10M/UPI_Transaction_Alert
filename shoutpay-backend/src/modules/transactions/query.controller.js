"use strict";

const service = require('./query.service');

function list(prisma) {
  return async (req, res, next) => {
    try {
      const result = await service.getTransactions(prisma, req.auth.merchantId, req.validatedQuery || req.query);
      return res.status(200).json(result);
    } catch (error) {
      return next(error);
    }
  };
}

function summary(prisma) {
  return async (req, res, next) => {
    try {
      const result = await service.getSummary(prisma, req.auth.merchantId);
      return res.status(200).json(result);
    } catch (error) {
      return next(error);
    }
  };
}

module.exports = { list, summary };
