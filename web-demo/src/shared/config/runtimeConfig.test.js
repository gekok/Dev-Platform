import assert from 'node:assert/strict';
import test from 'node:test';
import {
  resolveApiProxyTarget,
  resolveRuntimeConfig,
  RuntimeConfigurationError,
} from './runtimeConfig.js';

test('resolves explicit Demo mode and same-origin API base URL', () => {
  assert.deepEqual(
    resolveRuntimeConfig({ VITE_APP_MODE: 'demo', VITE_API_BASE_URL: '/api/v1' }),
    { mode: 'demo', apiBaseUrl: '/api/v1' },
  );
});

test('resolves explicit Backend mode and absolute Gateway API URL', () => {
  assert.deepEqual(
    resolveRuntimeConfig({
      VITE_APP_MODE: 'backend',
      VITE_API_BASE_URL: 'https://gateway.example.test/api/v1/',
    }),
    { mode: 'backend', apiBaseUrl: 'https://gateway.example.test/api/v1' },
  );
});

test('rejects missing and unsupported modes instead of falling back', () => {
  assert.throws(
    () => resolveRuntimeConfig({ VITE_API_BASE_URL: '/api/v1' }),
    RuntimeConfigurationError,
  );
  assert.throws(
    () => resolveRuntimeConfig({ VITE_APP_MODE: 'mock', VITE_API_BASE_URL: '/api/v1' }),
    /VITE_APP_MODE must be explicitly set/,
  );
});

test('rejects a missing or invalid API base URL', () => {
  assert.throws(
    () => resolveRuntimeConfig({ VITE_APP_MODE: 'backend' }),
    /VITE_API_BASE_URL is required/,
  );
  assert.throws(
    () => resolveRuntimeConfig({ VITE_APP_MODE: 'demo', VITE_API_BASE_URL: 'api/v1' }),
    /VITE_API_BASE_URL must be/,
  );
  assert.throws(
    () => resolveRuntimeConfig({ VITE_APP_MODE: 'backend', VITE_API_BASE_URL: 'ftp://gateway/api/v1' }),
    /VITE_API_BASE_URL must be/,
  );
});

test('requires an explicit Gateway proxy target for relative Backend URLs in dev', () => {
  assert.throws(
    () => resolveApiProxyTarget(
      { VITE_API_BASE_URL: '/api/v1' },
      { mode: 'backend', command: 'serve' },
    ),
    /VITE_API_PROXY_TARGET is required/,
  );

  assert.equal(
    resolveApiProxyTarget(
      { VITE_API_BASE_URL: '/api/v1', VITE_API_PROXY_TARGET: 'http://localhost:8080' },
      { mode: 'backend', command: 'serve' },
    ),
    'http://localhost:8080',
  );
});

test('rejects malformed proxy targets', () => {
  assert.throws(
    () => resolveApiProxyTarget(
      { VITE_API_PROXY_TARGET: '/gateway' },
      { mode: 'backend', command: 'serve' },
    ),
    /VITE_API_PROXY_TARGET must be/,
  );
  assert.throws(
    () => resolveApiProxyTarget(
      { VITE_API_PROXY_TARGET: 'http://localhost:8080/api' },
      { mode: 'backend', command: 'serve' },
    ),
    /without a path/,
  );
});
