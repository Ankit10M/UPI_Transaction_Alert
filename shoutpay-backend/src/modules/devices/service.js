"use strict";

const repository = require('./repository');

/**
 * Service layer — business rules for device management.
 * Never trusts client-supplied merchantId; always uses JWT.sub/JWT.deviceId.
 */

async function getDevices(prisma, merchantId, currentDeviceId) {
  const merchant = await repository.findMerchantByMerchantId(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }
  const devices = await repository.findDevicesByMerchantId(prisma, merchantId);
  return devices.map((device) => {
    const latestSession = device.authSessions && device.authSessions[0] ? device.authSessions[0] : null;
    const lastUsedAt = latestSession ? latestSession.lastUsedAt : device.updatedAt || device.createdAt;
    return {
      deviceId: device.deviceId,
      deviceName: device.deviceName,
      active: device.deviceId === currentDeviceId,
      lastUsedAt,
      createdAt: device.createdAt
    };
  });
}

async function logoutDevice(prisma, merchantId, deviceId) {
  const merchant = await repository.findMerchantByMerchantId(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }
  const device = await repository.findDeviceForMerchant(prisma, merchantId, deviceId);
  if (!device) {
    const err = new Error('Device not found');
    err.status = 404;
    err.code = 'DEVICE_NOT_FOUND';
    throw err;
  }
  await repository.revokeDevice(prisma, merchantId, deviceId);
  return { revokedDeviceId: deviceId };
}

async function logoutOtherDevices(prisma, merchantId, currentDeviceId) {
  const merchant = await repository.findMerchantByMerchantId(prisma, merchantId);
  if (!merchant) {
    const err = new Error('Merchant not found');
    err.status = 404;
    err.code = 'MERCHANT_NOT_FOUND';
    throw err;
  }
  const currentDevice = await repository.findDeviceForMerchant(prisma, merchantId, currentDeviceId);
  if (!currentDevice) {
    const err = new Error('Current device not found');
    err.status = 404;
    err.code = 'DEVICE_NOT_FOUND';
    throw err;
  }
  await repository.revokeOtherDevices(prisma, merchantId, currentDeviceId);
  return { currentDeviceId };
}

module.exports = { getDevices, logoutDevice, logoutOtherDevices };
