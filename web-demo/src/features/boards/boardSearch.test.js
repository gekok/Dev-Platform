import assert from 'node:assert/strict';
import { test } from 'node:test';
import { filterBoards } from './boardSearch.js';

const boards = [
  { id: 11, name: 'Platform Roadmap', key: 'PLAT' },
  { id: 12, name: 'Operations', key: 'OPS1' },
];

test('searches Board names case-insensitively', () => {
  assert.deepEqual(filterBoards(boards, 'road'), [boards[0]]);
});

test('searches Board keys independently of their names', () => {
  assert.deepEqual(filterBoards(boards, 'ops1'), [boards[1]]);
});

test('returns every Board when the search is blank', () => {
  assert.deepEqual(filterBoards(boards, '  '), boards);
});
