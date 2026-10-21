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
  assert.deepEqual(queryKeys.cards('11'), ['cards', '11']);
  assert.deepEqual(queryKeys.card(201), ['card', 201]);
  assert.deepEqual(queryKeys.comments(201), ['comments', 201]);
  assert.deepEqual(queryKeys.notifications('user-1'), ['notifications', 'user-1']);
});

test('default-board preference keys are scoped to the signed-in user', () => {
  assert.deepEqual(queryKeys.defaultBoards('user-1'), ['defaultBoards', 'user-1']);
  assert.notDeepEqual(
    queryKeys.defaultBoards('user-1'),
    queryKeys.defaultBoards('user-2'),
  );
});
