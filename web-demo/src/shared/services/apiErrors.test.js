import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getApiErrorMessage,
  getApiErrorStatus,
  isRetryableApiError,
  normalizeApiError,
} from './apiErrors.js';

test('normalizes unauthorized and forbidden responses without retrying', () => {
  const unauthorized = normalizeApiError({ response: { status: 401, data: {} } });
  const forbidden = normalizeApiError({ response: { status: 403, data: {} } });

  assert.equal(unauthorized.kind, 'authentication');
  assert.equal(unauthorized.message, 'Your session has expired. Sign in again.');
  assert.equal(unauthorized.retryable, false);
  assert.equal(forbidden.kind, 'permission');
  assert.equal(forbidden.message, 'You do not have permission to perform this action.');
  assert.equal(forbidden.retryable, false);
});

test('preserves login rejection details instead of calling them expired sessions', () => {
  const error = {
    config: { url: '/auth/login' },
    response: {
      status: 401,
      data: { code: 'UNAUTHORIZED', message: 'Invalid email or password' },
    },
  };

  assert.equal(normalizeApiError(error).message, 'Invalid email or password');
});

test('preserves a backend error code and message for client errors', () => {
  const normalized = normalizeApiError({
    response: {
      status: 400,
      data: { code: 'INVALID_CURSOR', message: 'Cursor is invalid.' },
    },
  });

  assert.equal(normalized.code, 'INVALID_CURSOR');
  assert.equal(normalized.message, 'Cursor is invalid.');
  assert.equal(normalized.retryable, false);
});

test('normalizes 5xx responses as retryable service failures', () => {
  const error = { response: { status: 503, data: {} } };

  assert.equal(normalizeApiError(error).kind, 'server');
  assert.equal(getApiErrorMessage(error), 'The service is temporarily unavailable. Try again later.');
  assert.equal(isRetryableApiError(error), true);
});

test('normalizes network and timeout failures as retryable', () => {
  const networkError = { isAxiosError: true, code: 'ERR_NETWORK' };
  const timeoutError = { isAxiosError: true, code: 'ECONNABORTED' };

  assert.equal(normalizeApiError(networkError).kind, 'network');
  assert.match(getApiErrorMessage(networkError), /Could not reach the API/);
  assert.match(normalizeApiError(timeoutError).message, /timed out/);
  assert.equal(isRetryableApiError(networkError), true);
});

test('exposes normalized status while preserving a fallback for non-API errors', () => {
  const error = { apiError: { status: 403, message: 'No access.', retryable: false } };

  assert.equal(getApiErrorStatus(error), 403);
  assert.equal(getApiErrorMessage(new Error('local error'), 'Local fallback.'), 'Local fallback.');
  assert.equal(isRetryableApiError(error), false);
});
