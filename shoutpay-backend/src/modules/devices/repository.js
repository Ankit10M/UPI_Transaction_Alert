"use strict";

/**
 * Device repository — only database operations, no business logic.
 * All merchant isolation is enforced via merchantId lookups; no client-trusted IDs.
 */

async function findMerchantByMerchantId(prisma, merchantId) {
  return prisma.merchant.findUnique({ where: { merchantId } });
}

async function findDevicesByMerchantId(prisma, merchantId) {
  const merchant = await prisma.merchant.findUnique({ where: { merchantId } });
  if (!merchant) return [];
  // Include latest sessions for lastUsedAt; order devices by creation
  return prisma.device.findMany({
    where: { merchantId: merchant.id },
    orderBy: { createdAt: 'asc' },
    include: {
      authSessions: {
        orderBy: { lastUsedAt: 'desc' },
        // we only need latest, but fetch all for mapping; limit to 1 via take
        take: 1
      }
    }
  });
}

async function findDeviceForMerchant(prisma, merchantId, deviceId) {
  const merchant = await prisma.merchant.findUnique({ where: { merchantId } });
  if (!merchant) return null;
  return prisma.device.findFirst({
    where: { deviceId, merchantId: merchant.id }
  });
}

/**
 * Revoke all active sessions for a specific device belonging to merchant.
 * Returns device if found, otherwise null. Does NOT touch refreshTokens (test 7).
 */
async function revokeDevice(prisma, merchantId, deviceId) {
  const merchant = await prisma.merchant.findUnique({ where: { merchantId } });
  if (!merchant) return null;
  const device = await prisma.device.findFirst({
    where: { deviceId, merchantId: merchant.id }
  });
  if (!device) return null;
  const now = new Date();
  await prisma.authSession.updateMany({
    where: {
      merchantId: merchant.id,
      deviceId: device.id,
      revokedAt: null
    },
    data: { revokedAt: now }
  });
  return device;
}

/**
 * Revoke all active sessions for other devices, keep current device active.
 * Returns current device if found, otherwise null.
 */
async function revokeOtherDevices(prisma, merchantId, currentDeviceId) {
  const merchant = await prisma.merchant.findUnique({ where: { merchantId } });
  if (!merchant) return null;
  const currentDevice = await prisma.device.findFirst({
    where: { deviceId: currentDeviceId, merchantId: merchant.id }
  });
  if (!currentDevice) return null;
  const now = new Date();
  await prisma.authSession.updateMany({
    where: {
      merchantId: merchant.id,
      deviceId: { not: currentDevice.id },
      revokedAt: null
    },
    data: { revokedAt: now }
  });
  return currentDevice;
}

module.exports = {
  findMerchantByMerchantId,
  findDevicesByMerchantId,
  findDeviceForMerchant,
  revokeDevice,
  revokeOtherDevices
};
