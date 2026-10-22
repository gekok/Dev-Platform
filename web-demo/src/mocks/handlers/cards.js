import { http, HttpResponse } from 'msw';
import {
  authenticateRequest,
  errorResponse,
  parsePositiveId,
  readJson,
} from './utils.js';

const OFFSET_TIMESTAMP_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|([+-])(\d{2}):(\d{2}))$/i;

function isValidOffsetTimestamp(value) {
  const match = OFFSET_TIMESTAMP_PATTERN.exec(value);
  if (!match) return false;

  const [, rawYear, rawMonth, rawDay, rawHour, rawMinute, rawSecond = '0'] = match;
  const year = Number(rawYear);
  const month = Number(rawMonth);
  const day = Number(rawDay);
  const hour = Number(rawHour);
  const minute = Number(rawMinute);
  const second = Number(rawSecond);
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

  if (
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > daysInMonth[month - 1] ||
    hour > 23 ||
    minute > 59 ||
    second > 59
  ) {
    return false;
  }

  if (match[9]) {
    const offsetHours = Number(match[10]);
    const offsetMinutes = Number(match[11]);
    if (offsetHours > 18 || offsetMinutes > 59 || (offsetHours === 18 && offsetMinutes !== 0)) {
      return false;
    }
  }

  return Number.isFinite(Date.parse(value));
}

function mapCard(card) {
  return {
    id: card.id,
    boardId: card.boardId,
    columnId: card.columnId,
    title: card.title,
    status: card.status,
    position: card.position,
    assigneeId: card.assigneeId,
  };
}

function mapCardDetail(card) {
  return {
    ...mapCard(card),
    description: card.description,
    dueDate: card.dueDate,
  };
}

function mapComment(comment) {
  return {
    id: comment.id,
    cardId: comment.cardId,
    authorId: comment.authorId,
    content: comment.content,
    createdAt: comment.createdAt,
  };
}

function authorizeProjectSnapshot(request, authentication, projectId) {
  const project = authentication.data.projects.find((item) => item.id === projectId);
  if (!project) {
    return {
      response: errorResponse(request, 404, 'NOT_FOUND', 'Project not found'),
    };
  }

  const membership = authentication.data.projectMembers.find(
    (item) => item.projectId === projectId && item.userId === authentication.user.id,
  );
  if (!membership) {
    return {
      response: errorResponse(request, 403, 'FORBIDDEN', 'Project access is forbidden'),
    };
  }

  return { project, membership };
}

function authorizeBoard(request, repository, resolveUserFromToken, boardId) {
  const authentication = authenticateRequest(
    request,
    repository,
    resolveUserFromToken,
  );
  if (authentication.response) return authentication;

  const board = authentication.data.boards.find((item) => item.id === boardId);
  if (!board) {
    return {
      response: errorResponse(request, 404, 'NOT_FOUND', 'Board not found'),
    };
  }

  const projectAccess = authorizeProjectSnapshot(
    request,
    authentication,
    board.projectId,
  );
  if (projectAccess.response) return projectAccess;

  return { ...authentication, board, ...projectAccess };
}

function authorizeCard(request, repository, resolveUserFromToken, cardId) {
  const authentication = authenticateRequest(
    request,
    repository,
    resolveUserFromToken,
  );
  if (authentication.response) return authentication;

  const card = authentication.data.cards.find((item) => item.id === cardId);
  if (!card) {
    return {
      response: errorResponse(request, 404, 'NOT_FOUND', 'Card not found'),
    };
  }

  const board = authentication.data.boards.find((item) => item.id === card.boardId);
  if (!board) {
    return {
      response: errorResponse(request, 404, 'NOT_FOUND', 'Board not found'),
    };
  }

  const projectAccess = authorizeProjectSnapshot(
    request,
    authentication,
    board.projectId,
  );
  if (projectAccess.response) return projectAccess;

  return { ...authentication, card, board, ...projectAccess };
}

function isProjectMember(data, projectId, userId) {
  return data.projectMembers.some(
    (membership) =>
      membership.projectId === projectId && membership.userId === userId,
  );
}

function addNotification(draft, allocateId, notification) {
  draft.notifications.push({
    id: allocateId('notification'),
    ...notification,
    read: false,
    createdAt: new Date().toISOString(),
  });
}

function notifyProjectMembers(draft, allocateId, projectId, actorId, notification) {
  for (const membership of draft.projectMembers) {
    if (membership.projectId !== projectId || membership.userId === actorId) continue;

    addNotification(draft, allocateId, {
      ...notification,
      recipientId: membership.userId,
    });
  }
}

function parseCreateCardInput(request, payload, authorization) {
  const columnId = parsePositiveId(payload?.columnId);
  const column = authorization.data.columns.find(
    (item) => item.id === columnId && item.boardId === authorization.board.id,
  );
  if (!column) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Column must belong to this Board',
      ),
    };
  }

  const title = typeof payload.title === 'string' ? payload.title.trim() : '';
  if (!title || title.length > 500) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Card title must contain 1 to 500 characters',
      ),
    };
  }

  const description = payload.description ?? null;
  if (description !== null && typeof description !== 'string') {
    return {
      response: errorResponse(request, 400, 'BAD_REQUEST', 'Description must be text'),
    };
  }

  const dueDate = payload.dueDate ?? null;
  if (
    dueDate !== null &&
    (typeof dueDate !== 'string' ||
      !isValidOffsetTimestamp(dueDate))
  ) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Due date must be a timestamp with an explicit time zone offset',
      ),
    };
  }

  const assigneeId = payload.assigneeId ?? null;
  if (
    assigneeId !== null &&
    (typeof assigneeId !== 'string' ||
      !isProjectMember(authorization.data, authorization.project.id, assigneeId))
  ) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Assignee must be a member of this Project',
      ),
    };
  }

  return { columnId, title, description, assigneeId, dueDate };
}

function sortCards(cards, columns) {
  const columnPositions = new Map(columns.map((column) => [column.id, column.position]));
  return cards.sort((left, right) =>
    (columnPositions.get(left.columnId) - columnPositions.get(right.columnId)) ||
    (left.position - right.position) ||
    (left.id - right.id),
  );
}

export function createCardHandlers(repository, resolveUserFromToken) {
  return [
    http.get('*/api/v1/boards/:boardId/cards', ({ request, params }) => {
      const boardId = parsePositiveId(params.boardId);
      if (!boardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Board not found');
      }

      const authorization = authorizeBoard(
        request,
        repository,
        resolveUserFromToken,
        boardId,
      );
      if (authorization.response) return authorization.response;

      const rawColumnId = new URL(request.url).searchParams.get('columnId');
      const columnId = rawColumnId === null ? null : parsePositiveId(rawColumnId);
      if (rawColumnId !== null && !columnId) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Column ID must be a positive integer');
      }
      if (
        columnId !== null &&
        !authorization.data.columns.some(
          (column) => column.id === columnId && column.boardId === boardId,
        )
      ) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Column must belong to this Board');
      }

      const cards = authorization.data.cards.filter(
        (card) =>
          card.boardId === boardId &&
          (columnId === null || card.columnId === columnId),
      );
      return HttpResponse.json(
        sortCards(cards, authorization.data.columns).map(mapCard),
      );
    }),
    http.post('*/api/v1/boards/:boardId/cards', async ({ request, params }) => {
      const boardId = parsePositiveId(params.boardId);
      if (!boardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Board not found');
      }

      const authorization = authorizeBoard(
        request,
        repository,
        resolveUserFromToken,
        boardId,
      );
      if (authorization.response) return authorization.response;

      const payload = await readJson(request);
      const parsed = parseCreateCardInput(request, payload, authorization);
      if (parsed.response) return parsed.response;

      const result = repository.transact((draft, allocateId) => {
        const column = draft.columns.find(
          (item) => item.id === parsed.columnId && item.boardId === boardId,
        );
        if (!column) return { invalidColumn: true };
        if (
          parsed.assigneeId !== null &&
          !isProjectMember(draft, authorization.project.id, parsed.assigneeId)
        ) {
          return { invalidAssignee: true };
        }

        const position = draft.cards
          .filter((card) => card.boardId === boardId && card.columnId === column.id)
          .reduce((currentMax, card) => Math.max(currentMax, card.position), -1) + 1;
        const card = {
          id: allocateId('card'),
          boardId,
          columnId: column.id,
          title: parsed.title,
          description: parsed.description,
          status: 'TODO',
          position,
          assigneeId: parsed.assigneeId,
          dueDate: parsed.dueDate,
        };
        draft.cards.push(card);

        if (card.assigneeId && card.assigneeId !== authorization.user.id) {
          addNotification(draft, allocateId, {
            recipientId: card.assigneeId,
            type: 'ASSIGNED',
            title: `A card was assigned to you: ${card.title}`,
            cardId: card.id,
            boardId,
          });
        }

        return { card };
      });

      if (result.invalidColumn) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Column must belong to this Board');
      }
      if (result.invalidAssignee) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Assignee must be a member of this Project',
        );
      }

      return HttpResponse.json(mapCard(result.card), { status: 201 });
    }),
    http.get('*/api/v1/cards/:cardId', ({ request, params }) => {
      const cardId = parsePositiveId(params.cardId);
      if (!cardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }

      const authorization = authorizeCard(
        request,
        repository,
        resolveUserFromToken,
        cardId,
      );
      if (authorization.response) return authorization.response;

      return HttpResponse.json(mapCardDetail(authorization.card));
    }),
    http.patch('*/api/v1/cards/:cardId/move', async ({ request, params }) => {
      const cardId = parsePositiveId(params.cardId);
      if (!cardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }

      const authorization = authorizeCard(
        request,
        repository,
        resolveUserFromToken,
        cardId,
      );
      if (authorization.response) return authorization.response;

      const payload = await readJson(request);
      const toColumnId = parsePositiveId(payload?.toColumnId);
      const toPosition = payload?.toPosition;
      if (
        !toColumnId ||
        !Number.isSafeInteger(toPosition) ||
        toPosition < 0 ||
        !authorization.data.columns.some(
          (column) => column.id === toColumnId && column.boardId === authorization.board.id,
        )
      ) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Destination Column and position must be valid for this Board',
        );
      }

      const result = repository.transact((draft, allocateId) => {
        const card = draft.cards.find((item) => item.id === cardId);
        const destinationColumn = draft.columns.find(
          (column) =>
            column.id === toColumnId && column.boardId === authorization.board.id,
        );
        if (!card || !destinationColumn) return { invalidDestination: true };

        const sourceColumnId = card.columnId;
        const sourceCards = draft.cards
          .filter(
            (item) =>
              item.boardId === authorization.board.id &&
              item.columnId === sourceColumnId &&
              item.id !== card.id,
          )
          .sort((left, right) => left.position - right.position);
        const destinationCards = toColumnId === sourceColumnId
          ? sourceCards
          : draft.cards
            .filter(
              (item) =>
                item.boardId === authorization.board.id &&
                item.columnId === toColumnId,
            )
            .sort((left, right) => left.position - right.position);

        if (toPosition > destinationCards.length) return { invalidPosition: true };

        const sourcePosition = card.position;
        sourceCards.forEach((item, position) => {
          item.position = position;
        });
        destinationCards.splice(toPosition, 0, card);
        card.columnId = toColumnId;
        destinationCards.forEach((item, position) => {
          item.position = position;
        });

        if (sourceColumnId !== toColumnId || sourcePosition !== toPosition) {
          notifyProjectMembers(
            draft,
            allocateId,
            authorization.project.id,
            authorization.user.id,
            {
              type: 'MOVED',
              title: `Card moved: ${card.title}`,
              cardId: card.id,
              boardId: card.boardId,
            },
          );
        }

        return { card };
      });

      if (result.invalidDestination) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Destination Column must belong to this Board');
      }
      if (result.invalidPosition) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Card position is outside the destination Column');
      }

      return HttpResponse.json(mapCard(result.card));
    }),
    http.patch('*/api/v1/cards/:cardId/assignee', async ({ request, params }) => {
      const cardId = parsePositiveId(params.cardId);
      if (!cardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }

      const authorization = authorizeCard(
        request,
        repository,
        resolveUserFromToken,
        cardId,
      );
      if (authorization.response) return authorization.response;

      const payload = await readJson(request);
      if (!payload || !Object.hasOwn(payload, 'assigneeId')) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Assignee ID is required');
      }
      const assigneeId = payload.assigneeId;
      if (
        assigneeId !== null &&
        (typeof assigneeId !== 'string' ||
          !isProjectMember(authorization.data, authorization.project.id, assigneeId))
      ) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Assignee must be a member of this Project',
        );
      }

      const result = repository.transact((draft, allocateId) => {
        const card = draft.cards.find((item) => item.id === cardId);
        if (!card) return { missingCard: true };
        if (
          assigneeId !== null &&
          !isProjectMember(draft, authorization.project.id, assigneeId)
        ) {
          return { invalidAssignee: true };
        }

        if (card.assigneeId !== assigneeId) {
          card.assigneeId = assigneeId;
          if (assigneeId && assigneeId !== authorization.user.id) {
            addNotification(draft, allocateId, {
              recipientId: assigneeId,
              type: 'ASSIGNED',
              title: `A card was assigned to you: ${card.title}`,
              cardId: card.id,
              boardId: card.boardId,
            });
          }
        }

        return { card };
      });
      if (result.missingCard) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }
      if (result.invalidAssignee) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Assignee must be a member of this Project',
        );
      }

      return HttpResponse.json(mapCard(result.card));
    }),
    http.get('*/api/v1/cards/:cardId/comments', ({ request, params }) => {
      const cardId = parsePositiveId(params.cardId);
      if (!cardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }

      const authorization = authorizeCard(
        request,
        repository,
        resolveUserFromToken,
        cardId,
      );
      if (authorization.response) return authorization.response;

      const comments = authorization.data.comments
        .filter((comment) => comment.cardId === cardId)
        .sort((left, right) =>
          left.createdAt.localeCompare(right.createdAt) || left.id - right.id,
        );
      return HttpResponse.json(comments.map(mapComment));
    }),
    http.post('*/api/v1/cards/:cardId/comments', async ({ request, params }) => {
      const cardId = parsePositiveId(params.cardId);
      if (!cardId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }

      const authorization = authorizeCard(
        request,
        repository,
        resolveUserFromToken,
        cardId,
      );
      if (authorization.response) return authorization.response;

      const payload = await readJson(request);
      const content = typeof payload?.content === 'string' ? payload.content.trim() : '';
      const mentionedUserIds = payload?.mentionedUserIds ?? [];
      if (!content) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'Comment content is required');
      }
      if (
        !Array.isArray(mentionedUserIds) ||
        mentionedUserIds.some(
          (userId) =>
            typeof userId !== 'string' ||
            !isProjectMember(authorization.data, authorization.project.id, userId),
        )
      ) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Mentioned users must be members of this Project',
        );
      }

      const uniqueMentionedUserIds = [...new Set(mentionedUserIds)];
      const createdAt = new Date().toISOString();
      const result = repository.transact((draft, allocateId) => {
        const card = draft.cards.find((item) => item.id === cardId);
        if (!card) return { missingCard: true };
        if (
          uniqueMentionedUserIds.some(
            (userId) => !isProjectMember(draft, authorization.project.id, userId),
          )
        ) {
          return { invalidMention: true };
        }

        const comment = {
          id: allocateId('comment'),
          cardId,
          authorId: authorization.user.id,
          content,
          createdAt,
          mentionedUserIds: uniqueMentionedUserIds,
        };
        draft.comments.push(comment);
        for (const userId of uniqueMentionedUserIds) {
          if (userId === authorization.user.id) continue;
          addNotification(draft, allocateId, {
            recipientId: userId,
            type: 'MENTIONED',
            title: `You were mentioned in a comment on: ${card.title}`,
            cardId: card.id,
            boardId: card.boardId,
          });
        }

        return { comment };
      });
      if (result.missingCard) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Card not found');
      }
      if (result.invalidMention) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Mentioned users must be members of this Project',
        );
      }

      return HttpResponse.json(mapComment(result.comment), { status: 201 });
    }),
  ];
}
