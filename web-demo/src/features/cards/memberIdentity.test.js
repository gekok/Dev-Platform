import assert from 'node:assert/strict';
import test from 'node:test';
import { getMemberDisplayName, getMemberUserId } from './memberIdentity.js';

test('reads documented userId and existing Demo id member projections', () => {
  assert.equal(getMemberUserId({ userId: 'backend-user' }), 'backend-user');
  assert.equal(getMemberUserId({ id: 'demo-user' }), 'demo-user');
  assert.equal(getMemberUserId({ userId: 'backend-user', id: 'demo-user' }), 'backend-user');
  assert.equal(getMemberUserId({}), null);
});

test('uses only the agreed fullName display field and otherwise keeps attribution generic', () => {
  assert.equal(getMemberDisplayName({ fullName: 'Project member' }), 'Project member');
  assert.equal(getMemberDisplayName({ userId: 'backend-user' }), 'Project member');
});
