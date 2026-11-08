import assert from 'node:assert/strict';
import test from 'node:test';
import api, { configureApiBaseUrl } from './axios.js';
import {
  clearSession,
  getAccessToken,
  SESSION_EXPIRED_EVENT,
  setAccessToken,
} from '../../features/auth/session.js';

function installWindow() {
  const previousWindow = globalThis.window;
  const values = new Map();
  const events = [];

  globalThis.window = {
    Event: class {
      constructor(type) {
        this.type = type;
      }
    },
    dispatchEvent(event) {
      events.push(event);
    },
    localStorage: {
      getItem(key) {
        return values.get(key) ?? null;
      },
      setItem(key, value) {
        values.set(key, String(value));
      },
      removeItem(key) {
        values.delete(key);
      },
    },
  };

  return {
    events,
    restore() {
      if (previousWindow === undefined) delete globalThis.window;
      else globalThis.window = previousWindow;
    },
  };
}

test('API requests attach the stored Bearer token', async () => {
  const browser = installWindow();
  configureApiBaseUrl('/api/v1');
  setAccessToken('signed-in-token');
  let requestConfig;

  try {
    await api.get('/projects', {
      adapter: async (config) => {
        requestConfig = config;
        return {
          config,
          data: [],
          headers: {},
          status: 200,
          statusText: 'OK',
        };
      },
    });

    assert.equal(requestConfig.headers.get('Authorization'), 'Bearer signed-in-token');
  } finally {
    clearSession();
    browser.restore();
  }
});

test('a 401 clears the session and emits the session-expired event', async () => {
  const browser = installWindow();
  configureApiBaseUrl('/api/v1');
  setAccessToken('expired-token');

  try {
    await assert.rejects(
      api.get('/projects', {
        adapter: async (config) => Promise.reject(Object.assign(new Error('Unauthorized'), {
          isAxiosError: true,
          config,
          response: {
            config,
            data: { code: 'UNAUTHORIZED' },
            headers: {},
            status: 401,
            statusText: 'Unauthorized',
          },
        })),
      }),
      (error) => error.apiError.status === 401,
    );

    assert.equal(getAccessToken(), null);
    assert.equal(browser.events.length, 1);
    assert.equal(browser.events[0].type, SESSION_EXPIRED_EVENT);
  } finally {
    clearSession();
    browser.restore();
  }
});

test('a login 401 without an authenticated request does not expire a session', async () => {
  const browser = installWindow();
  configureApiBaseUrl('/api/v1');

  try {
    await assert.rejects(
      api.post('/auth/login', { email: 'person@example.test', password: 'wrong' }, {
        adapter: async (config) => Promise.reject(Object.assign(new Error('Unauthorized'), {
          isAxiosError: true,
          config,
          response: {
            config,
            data: { code: 'UNAUTHORIZED', message: 'Invalid email or password' },
            headers: {},
            status: 401,
            statusText: 'Unauthorized',
          },
        })),
      }),
      (error) => {
        assert.equal(error.apiError.message, 'Invalid email or password');
        return true;
      },
    );

    assert.equal(browser.events.length, 0);
  } finally {
    browser.restore();
  }
});
