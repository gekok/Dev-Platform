import assert from 'node:assert/strict';
import test from 'node:test';
import { getProjectCardMetadata } from './projectSummary.js';

test('omits optional Project metadata that the backend has not returned', () => {
  assert.deepEqual(getProjectCardMetadata({ id: 12, name: 'New Project' }), []);
});

test('includes valid board count and creation date when available', () => {
  const metadata = getProjectCardMetadata({
    boardCount: 2,
    createdAt: '2026-11-08T09:00:00Z',
  });

  assert.deepEqual(metadata.map(({ key }) => key), ['boardCount', 'createdAt']);
  assert.equal(metadata[0].label, '2 boards');
  assert.match(metadata[1].label, /^Created /);
});

test('ignores invalid optional Project metadata', () => {
  assert.deepEqual(getProjectCardMetadata({ boardCount: -1, createdAt: 'invalid' }), []);
});
