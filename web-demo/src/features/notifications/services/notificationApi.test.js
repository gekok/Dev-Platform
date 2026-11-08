import assert from 'node:assert/strict';
import test from 'node:test';
import api, { configureApiBaseUrl } from '../../../shared/services/axios.js';
import { listMyNotifications } from './notificationApi.js';

test('requests the authenticated Notification inbox with the selected unread filter', async () => {
  let request;
  const previousAdapter = api.defaults.adapter;
  configureApiBaseUrl('/api/v1');
  api.defaults.adapter = async (config) => {
    request = config;
    return {
      config,
      data: [],
      headers: {},
      status: 200,
      statusText: 'OK',
    };
  };

  try {
    await listMyNotifications({ unreadOnly: true });

    assert.equal(request.method, 'get');
    assert.equal(request.url, '/notifications/me');
    assert.deepEqual(request.params, { unreadOnly: true });
  } finally {
    api.defaults.adapter = previousAdapter;
  }
});
