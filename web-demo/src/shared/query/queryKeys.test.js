import assert from 'node:assert/strict';
import { test } from 'node:test';
import { queryKeys } from './queryKeys.js';

test('Project list, detail, membership, and Board queries use stable Project-scoped keys', () => {
  assert.deepEqual(queryKeys.projects, ['projects']);
  assert.deepEqual(queryKeys.project('1'), ['project', '1']);
  assert.deepEqual(queryKeys.projectMembers('1'), ['projectMembers', '1']);
  assert.deepEqual(queryKeys.projectBoards('1'), ['projectBoards', '1']);
  assert.notDeepEqual(queryKeys.project('1'), queryKeys.project('2'));
  assert.notDeepEqual(queryKeys.projectMembers('1'), queryKeys.projectMembers('2'));
  assert.notDeepEqual(queryKeys.projectBoards('1'), queryKeys.projectBoards('2'));
});

test('Board and Card detail keys stay scoped to their resource IDs', () => {
  assert.deepEqual(queryKeys.board('11'), ['board', '11']);
  assert.deepEqual(queryKeys.cards('1', '11'), ['cards', '1', '11']);
  assert.deepEqual(queryKeys.cards('1', '11', 101), ['cards', '1', '11', 101]);
  assert.notDeepEqual(queryKeys.cards('1', '11', 101), queryKeys.cards('1', '11', 102));
  assert.deepEqual(queryKeys.card('1', 201), ['card', '1', 201]);
  assert.deepEqual(queryKeys.comments('1', 201), ['comments', '1', 201]);
  assert.deepEqual(queryKeys.notifications('user-1'), ['notifications', 'user-1']);
  assert.deepEqual(
    queryKeys.notifications('user-1', { unreadOnly: true }),
    ['notifications', 'user-1', { unreadOnly: true }],
  );
  assert.notDeepEqual(
    queryKeys.notifications('user-1', { unreadOnly: true }),
    queryKeys.notifications('user-1', { unreadOnly: false }),
  );
});

test('default-board preference keys are scoped to the signed-in user', () => {
  assert.deepEqual(queryKeys.defaultBoards('user-1'), ['defaultBoards', 'user-1']);
  assert.notDeepEqual(
    queryKeys.defaultBoards('user-1'),
    queryKeys.defaultBoards('user-2'),
  );
});
