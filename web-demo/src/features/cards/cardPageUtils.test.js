import assert from 'node:assert/strict';
import test from 'node:test';
import { flattenCardPages, hasDuplicateCardPages } from './cardPageUtils.js';

test('flattens cursor pages in stable position and ID descending order without duplicates', () => {
  const pages = [
    { items: [{ id: 7, position: 2 }, { id: 6, position: 2 }] },
    { items: [{ id: 6, position: 2 }, { id: 5, position: 1 }] },
  ];

  assert.deepEqual(flattenCardPages(pages).map((card) => card.id), [7, 6, 5]);
  assert.equal(hasDuplicateCardPages(pages), true);
  assert.equal(hasDuplicateCardPages([{ items: [{ id: 7 }] }, { items: [{ id: 6 }] }]), false);
});
