"use strict";

const Joi = require('joi');

/**
 * Validation layer — Joi schemas for device management.
 * Rejects empty values and unknown parameters; deviceId must be UUIDv4.
 */

const deviceIdParamSchema = Joi.object({
  deviceId: Joi.string().guid({ version: ['uuidv4'] }).required()
}).unknown(false);

function validateDeviceIdParam(req, res, next) {
  const { error, value } = deviceIdParamSchema.validate(req.params, {
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
  req.params = value;
  return next();
}

// For future query/body validation: reject unknown fields explicitly
const emptyBodySchema = Joi.object().unknown(false);

function rejectUnknownBody(req, res, next) {
  // Only enforce if body has keys (GET/DELETE typically empty)
  if (req.body && Object.keys(req.body).length > 0) {
    return res.status(400).json({
      error: { code: 'VALIDATION_ERROR', message: 'Unknown parameters not allowed' }
    });
  }
  return next();
}

module.exports = { deviceIdParamSchema, validateDeviceIdParam, rejectUnknownBody };
