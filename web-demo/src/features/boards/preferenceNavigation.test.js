import assert from 'node:assert/strict';
import { test } from 'node:test';
import { getPreferenceErrorClearPath } from './preferenceNavigation.js';

test('returns the current path while a preference error is shown there', () => {
  assert.equal(
    getPreferenceErrorClearPath({
      pathname: '/projects/1/boards/11',
      state: { defaultPreferenceError: 'Could not clear the default.' },
    }, 1),
    '/projects/1/boards/11',
  );
});

test('does not return a stale path after navigation clears the preference error state', () => {
  assert.equal(
    getPreferenceErrorClearPath({ pathname: '/projects/2/boards/21', state: null }, 2),
    null,
  );
});

test('does not clear another Project error after the mutation changes Projects', () => {
  assert.equal(
    getPreferenceErrorClearPath({
      pathname: '/projects/2/boards/21',
      state: { defaultPreferenceError: 'Project 2 still needs attention.' },
    }, 1),
    null,
  );
});
