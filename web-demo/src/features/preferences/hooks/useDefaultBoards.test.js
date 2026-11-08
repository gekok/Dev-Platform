import assert from 'node:assert/strict';
import test from 'node:test';
import { isDefaultBoardPreferenceEnabled } from './useDefaultBoards.js';

test('loads Board preferences only for an identified Demo user', () => {
  assert.equal(isDefaultBoardPreferenceEnabled('user-1', 'demo'), true);
  assert.equal(isDefaultBoardPreferenceEnabled('user-1', 'backend'), false);
  assert.equal(isDefaultBoardPreferenceEnabled('user-1', undefined), false);
  assert.equal(isDefaultBoardPreferenceEnabled(null, 'demo'), false);
});
