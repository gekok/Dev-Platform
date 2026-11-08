import assert from 'node:assert/strict';
import test from 'node:test';
import api, { configureApiBaseUrl } from '../../../shared/services/axios.js';
import { getCurrentUser, login, validateLoginSession } from './authApi.js';

function useAdapter(adapter) {
  const previousAdapter = api.defaults.adapter;
  configureApiBaseUrl('/api/v1');
  api.defaults.adapter = adapter;

  return () => {
    api.defaults.adapter = previousAdapter;
  };
}

test('login uses the backend TokenResponse contract', async () => {
  let request;
  const restore = useAdapter(async (config) => {
    request = config;
    return {
      config,
      data: { accessToken: 'signed-token', tokenType: 'Bearer' },
      headers: {},
      status: 200,
      statusText: 'OK',
    };
  });

  try {
    const session = await login({ email: 'person@example.test', password: 'secret' });

    assert.equal(request.method, 'post');
    assert.equal(request.url, '/auth/login');
    assert.deepEqual(JSON.parse(request.data), {
      email: 'person@example.test',
      password: 'secret',
    });
    assert.deepEqual(session, { accessToken: 'signed-token', tokenType: 'Bearer' });
  } finally {
    restore();
  }
});

test('rejects missing tokens and unsupported token types', () => {
  assert.throws(() => validateLoginSession(null), /valid Bearer token/);
  assert.throws(
    () => validateLoginSession({ accessToken: 'signed-token', tokenType: 'Basic' }),
    /valid Bearer token/,
  );
  assert.throws(
    () => validateLoginSession({ accessToken: '  ', tokenType: 'Bearer' }),
    /valid Bearer token/,
  );
});

test('current account uses the authenticated users/me endpoint', async () => {
  let request;
  const restore = useAdapter(async (config) => {
    request = config;
    return {
      config,
      data: {
        id: '00000000-0000-4000-8000-000000000001',
        email: 'person@example.test',
        fullName: 'Project Member',
        status: 'ACTIVE',
        authVersion: 1,
        roles: [],
      },
      headers: {},
      status: 200,
      statusText: 'OK',
    };
  });

  try {
    const user = await getCurrentUser();

    assert.equal(request.method, 'get');
    assert.equal(request.url, '/users/me');
    assert.equal(user.fullName, 'Project Member');
    assert.deepEqual(user.roles, []);
  } finally {
    restore();
  }
});
