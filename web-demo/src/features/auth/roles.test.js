import assert from 'node:assert/strict';
import { test } from 'node:test';
import { getSystemRoles, hasSystemRole, isSystemAdmin } from './roles.js';

test('recognizes SYSTEM_ADMIN from the current-user projection', () => {
  const user = { systemRoles: ['SYSTEM_ADMIN'] };

  assert.deepEqual(getSystemRoles(user), ['SYSTEM_ADMIN']);
  assert.equal(isSystemAdmin(user), true);
  assert.equal(hasSystemRole(user, 'USER'), false);
});

test('treats a user without system roles as a workspace user', () => {
  assert.deepEqual(getSystemRoles({ id: 'user-1' }), []);
  assert.equal(isSystemAdmin({ id: 'user-1' }), false);
  assert.equal(isSystemAdmin(null), false);
});

test('reads backend current-account roles without inventing a client role projection', () => {
  const user = { roles: ['SYSTEM_ADMIN'] };

  assert.deepEqual(getSystemRoles(user), ['SYSTEM_ADMIN']);
  assert.equal(isSystemAdmin(user), true);
});
