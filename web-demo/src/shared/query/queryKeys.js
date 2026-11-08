export const queryKeys = {
  projects: ['projects'],
  currentUser: ['currentUser'],
  project: (projectId) => ['project', projectId],
  projectMembers: (projectId) => ['projectMembers', projectId],
  projectInvitations: (projectId, status = 'PENDING') => ['projectInvitations', projectId, status],
  myProjectInvitations: (userId, status = 'PENDING') => ['myProjectInvitations', userId, status],
  projectInviteeSearch: (query) => ['projectInviteeSearch', query],
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
