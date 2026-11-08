export const queryKeys = {
  projects: ['projects'],
  currentUser: ['currentUser'],
  project: (projectId) => ['project', projectId],
  projectMembers: (projectId) => ['projectMembers', projectId],
  projectBoards: (projectId) => ['projectBoards', projectId],
  board: (boardId) => ['board', boardId],
  cards: (projectId, boardId, columnId) => columnId === undefined
    ? ['cards', projectId, boardId]
    : ['cards', projectId, boardId, columnId],
  card: (projectId, cardId) => ['card', projectId, cardId],
  comments: (projectId, cardId) => ['comments', projectId, cardId],
  notifications: (userId, filters) =>
    filters === undefined
      ? ['notifications', userId]
      : ['notifications', userId, filters],
  defaultBoards: (userId) => ['defaultBoards', userId],
};
