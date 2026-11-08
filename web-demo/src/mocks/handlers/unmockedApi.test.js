import assert from 'node:assert/strict';
import { test } from 'node:test';
import { setupServer } from 'msw/node';
import { missingDemoApiHandler } from './unmockedApi.js';

test('unknown Demo API requests return an explicit 501 response', async () => {
  const server = setupServer(missingDemoApiHandler);
  server.listen({ onUnhandledRequest: 'error' });

  try {
    const response = await fetch('http://localhost/api/v1/not-implemented');
    const body = await response.json();

    assert.equal(response.status, 501);
    assert.equal(body.code, 'DEMO_API_NOT_MOCKED');
    assert.match(body.message, /GET \/api\/v1\/not-implemented/);
  } finally {
    server.close();
  }
});
