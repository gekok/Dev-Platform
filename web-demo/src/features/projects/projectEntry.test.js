import assert from 'node:assert/strict';
import { test } from 'node:test';
import * as projectEntry from './projectEntry.js';

const { resolveProjectEntry } = projectEntry;

const projectBoards = [
  { id: 11, projectId: 1, name: 'Platform Roadmap' },
  { id: 12, projectId: 1, name: 'Operations' },
];

test('uses a saved Board only when it belongs to the Project board list', () => {
  const saved = resolveProjectEntry(projectBoards, '1', [
    { projectId: 1, boardId: 12 },
  ]);
  const missingFromList = resolveProjectEntry(projectBoards, '1', [
    { projectId: 1, boardId: 99 },
  ]);

  assert.deepEqual(saved, {
    board: projectBoards[1],
    isDefault: true,
    needsCleanup: false,
    cleanupError: null,
  });
  assert.deepEqual(missingFromList, {
    board: projectBoards[0],
    isDefault: false,
    needsCleanup: true,
    cleanupError: null,
  });
});

test('uses only the current Project preference when the preference list contains other Projects', () => {
  const result = resolveProjectEntry(projectBoards, '1', [
    { projectId: 2, boardId: 21 },
    { projectId: 1, boardId: 12 },
  ]);

  assert.equal(result.board, projectBoards[1]);
  assert.equal(result.isDefault, true);
  assert.equal(result.needsCleanup, false);
  assert.equal(result.cleanupError, null);
});

test('uses the first Board without marking it default when no preference exists', () => {
  const result = resolveProjectEntry(projectBoards, '1', []);

  assert.deepEqual(result, {
    board: projectBoards[0],
    isDefault: false,
    needsCleanup: false,
    cleanupError: null,
  });
});

test('returns the first Board and marks cleanup required for a stale preference', () => {
  const result = resolveProjectEntry(projectBoards, '1', [
    { projectId: 1, boardId: 99 },
  ]);

  assert.deepEqual(result, {
    board: projectBoards[0],
    isDefault: false,
    needsCleanup: true,
    cleanupError: null,
  });
});

test('keeps the no-Board result empty and marks stale cleanup required', () => {
  const result = resolveProjectEntry([], '1', [
    { projectId: 1, boardId: 99 },
  ]);

  assert.deepEqual(result, {
    board: null,
    isDefault: false,
    needsCleanup: true,
    cleanupError: null,
  });
});

test('does not mark a stale fallback as default when cleanup fails', () => {
  const cleanupError = new Error('Preference cleanup failed');
  const result = resolveProjectEntry(projectBoards, '1', [
    { projectId: 1, boardId: 99 },
  ], cleanupError);

  assert.deepEqual(result, {
    board: projectBoards[0],
    isDefault: false,
    needsCleanup: true,
    cleanupError,
  });
});

test('keeps the no-Board result and cleanup error when stale cleanup fails', () => {
  const cleanupError = new Error('Preference cleanup failed');
  const result = resolveProjectEntry([], '1', [
    { projectId: 1, boardId: 99 },
  ], cleanupError);

  assert.deepEqual(result, {
    board: null,
    isDefault: false,
    needsCleanup: true,
    cleanupError,
  });
});

test('applies a cleanup result only for the current mounted route attempt', () => {
  assert.equal(typeof projectEntry.isCurrentCleanupAttempt, 'function');
  const isCurrentCleanupAttempt = projectEntry.isCurrentCleanupAttempt;
  const currentAttempt = { key: 'project-b', status: 'pending' };

  assert.equal(isCurrentCleanupAttempt({
    isMounted: true,
    currentKey: 'project-b',
    currentAttempt,
    requestKey: 'project-b',
    requestAttempt: currentAttempt,
  }), true);
  assert.equal(isCurrentCleanupAttempt({
    isMounted: true,
    currentKey: 'project-b',
    currentAttempt,
    requestKey: 'project-a',
    requestAttempt: { key: 'project-a', status: 'pending' },
  }), false);
  assert.equal(isCurrentCleanupAttempt({
    isMounted: false,
    currentKey: 'project-b',
    currentAttempt,
    requestKey: 'project-b',
    requestAttempt: currentAttempt,
  }), false);
});

test('releases only the cleanup attempt owned by the leaving route effect', () => {
  assert.equal(typeof projectEntry.releaseCleanupAttempt, 'function');
  const releaseCleanupAttempt = projectEntry.releaseCleanupAttempt;
  const leavingAttempt = { key: 'project-a', status: 'pending' };
  const newerAttempt = { key: 'project-a', status: 'pending' };

  const routeChange = { currentKey: 'project-b', requestKey: 'project-a' };
  const sameRoute = { currentKey: 'project-a', requestKey: 'project-a' };

  assert.equal(releaseCleanupAttempt({
    ...routeChange,
    currentAttempt: leavingAttempt,
    requestAttempt: leavingAttempt,
  }), null);
  assert.equal(releaseCleanupAttempt({
    ...sameRoute,
    currentAttempt: leavingAttempt,
    requestAttempt: leavingAttempt,
  }), leavingAttempt);
  assert.equal(releaseCleanupAttempt({
    ...routeChange,
    currentAttempt: newerAttempt,
    requestAttempt: leavingAttempt,
  }), newerAttempt);
  assert.equal(releaseCleanupAttempt({
    ...routeChange,
    currentAttempt: { key: 'project-a', status: 'error' },
    requestAttempt: leavingAttempt,
  }).status, 'error');
});

test('reuses a matching pending cleanup attempt when the route effect is replayed', () => {
  assert.equal(typeof projectEntry.getCleanupAttemptForRoute, 'function');
  const getCleanupAttemptForRoute = projectEntry.getCleanupAttemptForRoute;
  const pendingAttempt = { key: 'project-a', status: 'pending' };

  assert.equal(getCleanupAttemptForRoute(pendingAttempt, 'project-a'), pendingAttempt);
  assert.equal(getCleanupAttemptForRoute(pendingAttempt, 'project-b'), null);
  assert.equal(getCleanupAttemptForRoute(null, 'project-a'), null);
});
