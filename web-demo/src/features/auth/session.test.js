import assert from 'node:assert/strict';
import test from 'node:test';
import { isAccessTokenStorageChange } from './session.js';

test('recognizes access-token storage changes for cross-tab session handling', () => {
  assert.equal(isAccessTokenStorageChange({ key: 'dev-platform-demo:access-token' }), true);
  assert.equal(isAccessTokenStorageChange({ key: 'unrelated-storage-key' }), false);
  assert.equal(isAccessTokenStorageChange(null), false);
});
