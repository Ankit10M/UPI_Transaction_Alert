"use strict";

const service = require('./service');

function getDevices(prisma) {
  return async (req, res, next) => {
    try {
      const devices = await service.getDevices(prisma, req.auth.merchantId, req.auth.deviceId);
      return res.json({ devices });
    } catch (error) {
      return next(error);
    }
  };
}

function deleteDevice(prisma) {
  return async (req, res, next) => {
    try {
      await service.logoutDevice(prisma, req.auth.merchantId, req.params.deviceId);
      return res.status(204).send();
    } catch (error) {
      return next(error);
    }
  };
}

function logoutOthers(prisma) {
  return async (req, res, next) => {
    try {
      await service.logoutOtherDevices(prisma, req.auth.merchantId, req.auth.deviceId);
      return res.status(204).send();
    } catch (error) {
      return next(error);
    }
  };
}

module.exports = { getDevices, deleteDevice, logoutOthers };
