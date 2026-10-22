import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normalizeCardId } from './cardIds.js';

test('normalizeCardId converts only positive decimal safe-integer IDs', () => {
  assert.equal(normalizeCardId(201), 201);
  assert.equal(normalizeCardId('201'), 201);
  assert.equal(normalizeCardId('001'), 1);
  assert.equal(normalizeCardId('1e2'), '1e2');
  assert.equal(normalizeCardId('0x10'), '0x10');
  assert.equal(normalizeCardId('9007199254740992'), '9007199254740992');
  assert.equal(normalizeCardId(null), null);
});
