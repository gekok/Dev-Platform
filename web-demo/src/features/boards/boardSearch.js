export function filterBoards(boards, query) {
  const normalizedQuery = query.trim().toLocaleLowerCase();
  if (!normalizedQuery) return boards;

  return boards.filter((board) =>
    `${board.name} ${board.key}`.toLocaleLowerCase().includes(normalizedQuery),
  );
}
