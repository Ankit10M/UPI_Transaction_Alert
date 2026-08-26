"use strict";

const Joi = require('joi');

const querySchema = Joi.object({
  page: Joi.number().integer().min(1).default(1),
  limit: Joi.number().integer().min(1).max(100).default(20),
  startDate: Joi.string().isoDate().optional(),
  endDate: Joi.string().isoDate().optional()
}).unknown(false);

function validateQuery(req, res, next) {
  const { error, value } = querySchema.validate(req.query, {
    abortEarly: false,
    stripUnknown: false,
    convert: true
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
  // Additional check: endDate must be >= startDate if both provided
  if (value.startDate && value.endDate) {
    const start = new Date(value.startDate);
    const end = new Date(value.endDate);
    if (end < start) {
      return res.status(400).json({
        error: { code: 'VALIDATION_ERROR', message: 'endDate must be after startDate' }
      });
    }
  }
  req.validatedQuery = value;
  return next();
}

module.exports = { querySchema, validateQuery };
