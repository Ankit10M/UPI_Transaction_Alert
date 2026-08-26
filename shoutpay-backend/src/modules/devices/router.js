"use strict";

const express = require('express');
const { requireAuth } = require('../../auth/middleware');
const { validateDeviceIdParam } = require('./validation');
const controller = require('./controller');

function devicesRouterFor(prisma) {
  const router = express.Router();

  // DELETE /api/auth/devices/logout-others must be before /:deviceId to avoid param capture
  router.get('/', requireAuth, controller.getDevices(prisma));
  router.delete('/logout-others', requireAuth, controller.logoutOthers(prisma));
  router.delete('/:deviceId', requireAuth, validateDeviceIdParam, controller.deleteDevice(prisma));

  return router;
}

module.exports = { devicesRouterFor };
