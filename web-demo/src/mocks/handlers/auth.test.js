import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DEMO_CREDENTIALS, DEMO_OWNER_ID } from '../data/seed.js';
import { DEMO_ACCESS_TOKEN } from './auth.js';
import {
  MEMBER_ID,
  MEMBER_TOKEN,
  apiUrl,
  bearer,
  withMockServer,
} from '../testUtils.js';

test('accepts the seeded credentials and returns a bearer token', async () => {
  await withMockServer(async () => {
    const response = await fetch(apiUrl('/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(DEMO_CREDENTIALS),
    });
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.equal(body.accessToken, DEMO_ACCESS_TOKEN);
    assert.equal(body.tokenType, 'Bearer');
  });
});

test('rejects invalid login credentials with the documented error shape', async () => {
  await withMockServer(async () => {
    const response = await fetch(apiUrl('/auth/login'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...DEMO_CREDENTIALS, password: 'wrong' }),
    });
    const body = await response.json();

    assert.equal(response.status, 401);
    assert.equal(body.code, 'UNAUTHORIZED');
    assert.equal(body.path, '/api/v1/auth/login');
    assert.ok(body.timestamp);
  });
});

test('resolves GET /users/me from the bearer identity', async () => {
  await withMockServer(async () => {
    const response = await fetch(apiUrl('/users/me'), {
      headers: bearer(MEMBER_TOKEN),
    });
    const body = await response.json();

    assert.equal(response.status, 200);
    assert.deepEqual(body, {
      id: MEMBER_ID,
      email: 'sam.nguyen@devplatform.demo',
      fullName: 'Sam Nguyen',
    });
    assert.notEqual(body.id, DEMO_OWNER_ID);
  });
});

test('returns 401 for missing or invalid bearer tokens', async () => {
  await withMockServer(async () => {
    for (const headers of [{}, bearer('unknown-token')]) {
      const response = await fetch(apiUrl('/users/me'), { headers });
      const body = await response.json();

      assert.equal(response.status, 401);
      assert.equal(body.code, 'UNAUTHORIZED');
      assert.equal(body.path, '/api/v1/users/me');
    }
  });
});
