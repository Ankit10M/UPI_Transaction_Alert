"use strict";

const express = require('express');
const { requireAuth } = require('../../auth/middleware');
const { validatePatchProfile } = require('./validation');
const controller = require('./controller');

function merchantRouterFor(prisma) {
  const router = express.Router();

  // GET /api/merchant/profile — authenticated merchant only, identity from JWT.sub
  router.get('/profile', requireAuth, controller.getProfile(prisma));

  // PATCH /api/merchant/profile — only ownerName/shopName, immutable fields rejected
  router.patch('/profile', requireAuth, validatePatchProfile, controller.updateProfile(prisma));

  return router;
}

module.exports = { merchantRouterFor };
