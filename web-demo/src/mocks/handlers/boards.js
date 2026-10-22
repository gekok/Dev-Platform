import { http, HttpResponse } from 'msw';
import {
  authenticateRequest,
  authorizeProject,
  errorResponse,
  parsePositiveId,
  readJson,
} from './utils.js';

const DEFAULT_COLUMN_NAMES = ['To Do', 'In Progress', 'Done'];
const BOARD_KEY_PATTERN = /^[A-Z0-9]{2,20}$/;

function mapBoard(board, data) {
  return {
    ...board,
    columnCount: data.columns.filter((column) => column.boardId === board.id).length,
  };
}

function mapBoardDetail(board, data) {
  return {
    ...board,
    columns: data.columns
      .filter((column) => column.boardId === board.id)
      .sort((left, right) => left.position - right.position),
  };
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

  const project = authentication.data.projects.find(
    (item) => item.id === board.projectId,
  );
  if (!project) {
    return {
      response: errorResponse(request, 404, 'NOT_FOUND', 'Project not found'),
    };
  }

  const membership = authentication.data.projectMembers.find(
    (item) => item.projectId === project.id && item.userId === authentication.user.id,
  );
  if (!membership) {
    return {
      response: errorResponse(request, 403, 'FORBIDDEN', 'Project access is forbidden'),
    };
  }

  return { ...authentication, board, project, membership };
}

function ownerOnly(request, authorization) {
  if (authorization.membership.role === 'OWNER') return null;
  return errorResponse(request, 403, 'FORBIDDEN', 'Project Owner access is required');
}

function parseBoardInput(request, payload) {
  const name = typeof payload?.name === 'string' ? payload.name.trim() : '';
  const key = typeof payload?.key === 'string' ? payload.key.trim() : '';
  if (!name || name.length > 100) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Board name must contain 1 to 100 characters',
      ),
    };
  }
  if (!BOARD_KEY_PATTERN.test(key)) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Board key must contain 2 to 20 uppercase letters or numbers',
      ),
    };
  }

  const sourceNames = payload.columns === undefined
    ? DEFAULT_COLUMN_NAMES
    : payload.columns;
  if (!Array.isArray(sourceNames) || sourceNames.length === 0) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'A Board must contain at least one Column',
      ),
    };
  }

  const columnNames = sourceNames.map((value) =>
    typeof value === 'string' ? value.trim() : '',
  );
  const normalizedNames = columnNames.map((value) => value.toLocaleLowerCase());
  if (
    columnNames.some((value) => !value || value.length > 50) ||
    new Set(normalizedNames).size !== normalizedNames.length
  ) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'Column names must be unique and contain 1 to 50 characters',
      ),
    };
  }

  return { name, key, columnNames };
}

export function createBoardHandlers(repository, resolveUserFromToken) {
  return [
    http.get('*/api/v1/projects/:projectId/boards', ({ request, params }) => {
      const projectId = parsePositiveId(params.projectId);
      if (!projectId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Project not found');
      }

      const authorization = authorizeProject(
        request,
        repository,
        resolveUserFromToken,
        projectId,
      );
      if (authorization.response) return authorization.response;

      const boards = authorization.data.boards
        .filter((board) => board.projectId === projectId)
        .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
        .map((board) => mapBoard(board, authorization.data));

      return HttpResponse.json(boards);
    }),
    http.post('*/api/v1/projects/:projectId/boards', async ({ request, params }) => {
      const projectId = parsePositiveId(params.projectId);
      if (!projectId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Project not found');
      }

      const authorization = authorizeProject(
        request,
        repository,
        resolveUserFromToken,
        projectId,
      );
      if (authorization.response) return authorization.response;
      const ownerError = ownerOnly(request, authorization);
      if (ownerError) return ownerError;

      const payload = await readJson(request);
      const parsed = parseBoardInput(request, payload);
      if (parsed.response) return parsed.response;

      const createdAt = new Date().toISOString();
      const creationResult = repository.transact((draft, allocateId) => {
        if (draft.boards.some((board) => board.key === parsed.key)) {
          return { conflict: true };
        }

        const board = {
          id: allocateId('board'),
          projectId,
          name: parsed.name,
          key: parsed.key,
          ownerId: authorization.user.id,
          createdAt,
        };
        draft.boards.push(board);
        const columns = parsed.columnNames.map((columnName, position) => ({
          id: allocateId('column'),
          boardId: board.id,
          name: columnName,
          position,
        }));
        draft.columns.push(...columns);
        return { conflict: false, board: { ...board, columns } };
      });
      if (creationResult.conflict) {
        return errorResponse(request, 409, 'CONFLICT', 'Board key is already in use');
      }

      return HttpResponse.json(creationResult.board, { status: 201 });
    }),
    http.get('*/api/v1/boards/:boardId', ({ request, params }) => {
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

      return HttpResponse.json(mapBoardDetail(authorization.board, authorization.data));
    }),
    http.post('*/api/v1/boards/:boardId/columns', async ({ request, params }) => {
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
      const ownerError = ownerOnly(request, authorization);
      if (ownerError) return ownerError;

      const payload = await readJson(request);
      const name = typeof payload?.name === 'string' ? payload.name.trim() : '';
      if (!name || name.length > 50) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Column name must contain 1 to 50 characters',
        );
      }
      const creationResult = repository.transact((draft, allocateId) => {
        const existingColumns = draft.columns.filter(
          (column) => column.boardId === boardId,
        );
        if (
          existingColumns.some(
            (column) => column.name.toLocaleLowerCase() === name.toLocaleLowerCase(),
          )
        ) {
          return { conflict: true };
        }

        const position = existingColumns.reduce(
          (currentMax, column) => Math.max(currentMax, column.position),
          -1,
        ) + 1;
        const column = {
          id: allocateId('column'),
          boardId,
          name,
          position,
        };
        draft.columns.push(column);
        return { conflict: false, column };
      });
      if (creationResult.conflict) {
        return errorResponse(request, 409, 'CONFLICT', 'Column name is already in use');
      }

      return HttpResponse.json(creationResult.column, { status: 201 });
    }),
    http.patch('*/api/v1/boards/:boardId/columns/order', async ({ request, params }) => {
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
      const ownerError = ownerOnly(request, authorization);
      if (ownerError) return ownerError;

      const payload = await readJson(request);
      const orderedColumnIds = payload?.orderedColumnIds;
      const requestedIds = Array.isArray(orderedColumnIds)
        ? orderedColumnIds
        : [];
      const reorderResult = repository.transact((draft) => {
        const columns = draft.columns.filter((column) => column.boardId === boardId);
        const currentIds = new Set(columns.map((column) => column.id));
        const hasValidOrder =
          requestedIds.length === columns.length &&
          requestedIds.every((id) => Number.isSafeInteger(id) && id > 0) &&
          new Set(requestedIds).size === requestedIds.length &&
          requestedIds.every((id) => currentIds.has(id));
        if (!hasValidOrder) return { invalidOrder: true };

        const positions = new Map(
          requestedIds.map((columnId, position) => [columnId, position]),
        );
        for (const column of draft.columns) {
          if (column.boardId === boardId) {
            column.position = positions.get(column.id);
          }
        }
        const board = draft.boards.find((item) => item.id === boardId);
        return { invalidOrder: false, board: mapBoardDetail(board, draft) };
      });
      if (reorderResult.invalidOrder) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Column order must include every Column in this Board exactly once',
        );
      }

      return HttpResponse.json(reorderResult.board);
    }),
  ];
}
