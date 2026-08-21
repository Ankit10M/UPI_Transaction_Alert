"use strict";

beforeEach(() => {
  jest.resetModules();
  process.env.JWT_SECRET = 'unit-test-secret-only';
  process.env.AUTH_ACCESS_TOKEN_TTL = '15m';
});

test('access token contains only approved identity claims and verifies fixed issuer/audience', () => {
  const { signAccessToken, verifyAccessToken } = require('../jwt');
  const token = signAccessToken({ merchantId: 'SP-000001', deviceId: '123e4567-e89b-42d3-a456-426614174000', sessionId: 'session-1' });
  const payload = verifyAccessToken(token);
  expect(payload.sub).toBe('SP-000001');
  expect(payload.deviceId).toBe('123e4567-e89b-42d3-a456-426614174000');
  expect(payload.jti).toBe('session-1');
  expect(payload.iss).toBe('shoutpay-backend');
  expect(payload.aud).toBe('shoutpay-backend-api');
  expect(payload.firebaseUid).toBeUndefined();
  expect(payload.phoneNumber).toBeUndefined();
});

test('invalid JWT signatures are rejected', () => {
  const { signAccessToken, verifyAccessToken } = require('../jwt');
  const token = signAccessToken({ merchantId: 'SP-000001', deviceId: '123e4567-e89b-42d3-a456-426614174000', sessionId: 'session-1' });
  expect(() => verifyAccessToken(`${token}x`)).toThrow();
});
