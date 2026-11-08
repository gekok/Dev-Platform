import assert from 'node:assert/strict';
import test from 'node:test';
import { startRuntime } from './runtimeStartup.js';
import { resolveRuntimeConfig } from './runtimeConfig.js';

test('Demo mode starts MSW before rendering the app', async () => {
  const calls = [];

  await startRuntime({
    mode: 'demo',
    startDemoWorker: async () => calls.push('msw'),
    renderApp: () => calls.push('render'),
  });

  assert.deepEqual(calls, ['msw', 'render']);
});

test('normalized uppercase Demo mode starts MSW before rendering the app', async () => {
  const calls = [];
  const runtime = resolveRuntimeConfig({
    MODE: 'demo',
    VITE_APP_MODE: 'DEMO',
    VITE_API_BASE_URL: '/api/v1',
  });

  await startRuntime({
    mode: runtime.mode,
    startDemoWorker: async () => calls.push('msw'),
    renderApp: () => calls.push('render'),
  });

  assert.deepEqual(calls, ['msw', 'render']);
});

test('Backend mode renders without importing or starting MSW', async () => {
  const calls = [];

  await startRuntime({
    mode: 'backend',
    startDemoWorker: async () => calls.push('msw'),
    renderApp: () => calls.push('render'),
  });

  assert.deepEqual(calls, ['render']);
});

test('invalid mode does not start MSW or render the app', async () => {
  const calls = [];

  await assert.rejects(
    startRuntime({
      mode: 'unknown',
      startDemoWorker: async () => calls.push('msw'),
      renderApp: () => calls.push('render'),
    }),
    /Runtime mode must be resolved/,
  );
  assert.deepEqual(calls, []);
});
