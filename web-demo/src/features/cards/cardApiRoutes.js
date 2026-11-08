export class CardPaginationContractPendingError extends Error {
  constructor() {
    super('Card list is disabled until the Project-scoped per-Column cursor contract is approved.');
    this.name = 'CardPaginationContractPendingError';
    this.code = 'CARD_PAGINATION_CONTRACT_PENDING';
  }
}

export function requireCardListContract(mode) {
  if (mode === 'backend') throw new CardPaginationContractPendingError();
  if (mode !== 'demo') {
    throw new Error('VITE_APP_MODE must be explicitly set to "demo" or "backend".');
  }
}

export function createCardApiRoutes(mode) {
  if (mode !== 'demo' && mode !== 'backend') {
    throw new Error('VITE_APP_MODE must be explicitly set to "demo" or "backend".');
  }

  if (mode === 'demo') {
    return Object.freeze({
      card: (_projectId, cardId) => `/cards/${cardId}`,
      comments: (_projectId, cardId) => `/cards/${cardId}/comments`,
      cards: (_projectId, boardId) => `/boards/${boardId}/cards`,
      createCard: (_projectId, boardId) => `/boards/${boardId}/cards`,
      moveCard: (_projectId, cardId) => `/cards/${cardId}/move`,
      assignCard: (_projectId, cardId) => `/cards/${cardId}/assignee`,
    });
  }

  return Object.freeze({
    card: (projectId, cardId) => `/projects/${projectId}/cards/${cardId}`,
    comments: (projectId, cardId) => `/projects/${projectId}/cards/${cardId}/comments`,
    cards: (projectId, boardId) => `/projects/${projectId}/boards/${boardId}/cards`,
    createCard: (projectId, boardId) => `/projects/${projectId}/boards/${boardId}/cards`,
    moveCard: (projectId, cardId) => `/projects/${projectId}/cards/${cardId}/move`,
    assignCard: (projectId, cardId) => `/projects/${projectId}/cards/${cardId}/assignee`,
  });
}
