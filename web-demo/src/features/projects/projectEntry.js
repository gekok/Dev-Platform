export function resolveProjectEntry(boards, projectId, defaultBoards, cleanupError = null) {
  const normalizedProjectId = Number(projectId);
  const projectBoards = Number.isSafeInteger(normalizedProjectId) && normalizedProjectId > 0
    ? (boards ?? []).filter(
        (board) => Number(board.projectId) === normalizedProjectId,
      )
    : [];
  const preference = (defaultBoards ?? []).find(
    (item) => Number(item.projectId) === normalizedProjectId,
  );
  const savedBoard = preference
    ? projectBoards.find((board) => Number(board.id) === Number(preference.boardId))
    : null;

  if (savedBoard) {
    return {
      board: savedBoard,
      isDefault: true,
      needsCleanup: false,
      cleanupError: null,
    };
  }

  const needsCleanup = Boolean(preference);
  return {
    board: projectBoards[0] ?? null,
    isDefault: false,
    needsCleanup,
    cleanupError: needsCleanup ? cleanupError : null,
  };
}

export function isCurrentCleanupAttempt({
  isMounted,
  currentKey,
  currentAttempt,
  requestKey,
  requestAttempt,
}) {
  return Boolean(
    isMounted &&
    currentKey === requestKey &&
    currentAttempt === requestAttempt,
  );
}

export function releaseCleanupAttempt({
  currentKey,
  currentAttempt,
  requestKey,
  requestAttempt,
}) {
  return currentKey !== requestKey && currentAttempt === requestAttempt
    ? null
    : currentAttempt;
}

export function getCleanupAttemptForRoute(currentAttempt, routeKey) {
  return currentAttempt?.key === routeKey ? currentAttempt : null;
}
