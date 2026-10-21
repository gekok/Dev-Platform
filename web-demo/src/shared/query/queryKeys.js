export const queryKeys = {
  projects: ['projects'],
  currentUser: ['currentUser'],
  project: (projectId) => ['project', projectId],
  projectMembers: (projectId) => ['projectMembers', projectId],
  projectBoards: (projectId) => ['projectBoards', projectId],
  board: (boardId) => ['board', boardId],
  cards: (boardId) => ['cards', boardId],
  card: (cardId) => ['card', cardId],
  comments: (cardId) => ['comments', cardId],
  notifications: (userId) => ['notifications', userId],
  defaultBoards: (userId) => ['defaultBoards', userId],
};
