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
