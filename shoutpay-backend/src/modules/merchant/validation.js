"use strict";

const Joi = require('joi');

/**
 * Validation layer — request validation using Joi (same library as auth module).
 * PATCH /profile: only ownerName and shopName are allowed.
 * Any immutable field must be rejected with 400, never stripped.
 */

const patchProfileSchema = Joi.object({
  ownerName: Joi.string().trim().min(1).max(120),
  shopName: Joi.string().trim().min(1).max(120),
  merchantId: Joi.forbidden(),
  firebaseUid: Joi.forbidden(),
  phoneNumber: Joi.forbidden(),
  createdAt: Joi.forbidden(),
  updatedAt: Joi.forbidden(),
  id: Joi.forbidden(),
  status: Joi.forbidden(),
})
  .min(1)
  .unknown(false)
  .messages({
    'object.unknown': 'Forbidden field "{#label}" is not allowed',
    'any.unknown': 'Forbidden field "{#label}" is not allowed',
  });

function validatePatchProfile(req, res, next) {
  const { error, value } = patchProfileSchema.validate(req.body, {
    abortEarly: false,
    stripUnknown: false,
  });
  if (error) {
    return res.status(400).json({
      error: {
        code: 'VALIDATION_ERROR',
        message: error.details.map((d) => d.message).join(', '),
        details: error.details.map((d) => d.message),
      },
    });
  }
  req.validatedBody = value;
  return next();
}

module.exports = { patchProfileSchema, validatePatchProfile };
