"use strict";

const Joi = require('joi');

/**
 * Validation for POST /api/transactions/sync
 * - transactions: required array min 1 max 100
 * - Each: transactionUuid UUID required, amount positive, senderName required, senderVpa optional, transactionTime ISO, deviceId required
 * - Reject forbidden fields: merchantId, firebaseUid, status, createdAt, id, currency (optional but not forbidden), etc.
 */

const transactionItemSchema = Joi.object({
  transactionUuid: Joi.string().guid({ version: ['uuidv4', 'uuidv1', 'uuidv5'] }).required(),
  deviceId: Joi.string().trim().min(1).max(100).required(),
  amount: Joi.number().positive().required(),
  senderName: Joi.string().trim().min(1).max(200).required(),
  senderVpa: Joi.string().trim().max(200).allow('', null).optional(),
  upiReference: Joi.string().trim().max(100).allow('', null).optional(),
  upiApp: Joi.string().trim().max(100).allow('', null).optional(),
  transactionTime: Joi.string().isoDate().required(),
  // Forbidden fields — must be rejected
  merchantId: Joi.forbidden(),
  firebaseUid: Joi.forbidden(),
  status: Joi.forbidden(),
  createdAt: Joi.forbidden(),
  updatedAt: Joi.forbidden(),
  id: Joi.forbidden(),
  currency: Joi.forbidden()
}).unknown(false);

const syncSchema = Joi.object({
  transactions: Joi.array().items(transactionItemSchema).min(1).max(100).required()
}).unknown(false);

function validateSync(req, res, next) {
  const { error, value } = syncSchema.validate(req.body, {
    abortEarly: false,
    stripUnknown: false
  });
  if (error) {
    return res.status(400).json({
      error: {
        code: 'VALIDATION_ERROR',
        message: error.details.map((d) => d.message).join(', '),
        details: error.details.map((d) => d.message)
      }
    });
  }
  req.validatedBody = value;
  return next();
}

module.exports = { syncSchema, transactionItemSchema, validateSync };
