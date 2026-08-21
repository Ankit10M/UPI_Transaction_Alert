"use strict";

const { durationToMilliseconds, generateRefreshToken, hashRefreshToken } = require('../tokens');

test('refresh tokens are random transport-safe values and only hashes are deterministic', () => {
  const token = generateRefreshToken();
  expect(token).toMatch(/^[A-Za-z0-9_-]+$/);
  expect(hashRefreshToken(token)).toHaveLength(64);
  expect(hashRefreshToken(token)).toBe(hashRefreshToken(token));
  expect(hashRefreshToken(token)).not.toBe(token);
});

test('configured authentication durations use approved units', () => {
  expect(durationToMilliseconds('15m')).toBe(900000);
  expect(durationToMilliseconds('30d')).toBe(2592000000);
  expect(() => durationToMilliseconds('forever')).toThrow();
});
