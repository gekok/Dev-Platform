import { http, HttpResponse } from 'msw';
import {
  authenticateRequest,
  authorizeProject,
  errorResponse,
  parsePositiveId,
  readJson,
} from './utils.js';

const DEFAULT_BOARDS_PATH = '*/api/v1/users/me/preferences/default-boards';

function parseBoardId(request, payload) {
  const boardId = parsePositiveId(String(payload?.boardId ?? ''));
  if (!boardId) {
    return {
      response: errorResponse(
        request,
        400,
        'BAD_REQUEST',
        'A valid Board ID is required',
      ),
    };
  }

  return { boardId };
}

export function createPreferenceHandlers(repository, resolveUserFromToken) {
  return [
    http.get(DEFAULT_BOARDS_PATH, ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const joinedProjectIds = new Set(
        authentication.data.projectMembers
          .filter((membership) => membership.userId === authentication.user.id)
          .map((membership) => membership.projectId),
      );
      const defaultBoards = authentication.data.defaultBoardPreferences
        .filter(
          (preference) =>
            preference.userId === authentication.user.id &&
            joinedProjectIds.has(preference.projectId),
        )
        .map(({ projectId, boardId }) => ({ projectId, boardId }))
        .sort((left, right) => left.projectId - right.projectId);

      return HttpResponse.json({ defaultBoards });
    }),
    http.put(`${DEFAULT_BOARDS_PATH}/:projectId`, async ({ request, params }) => {
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

      const payload = await readJson(request);
      const parsedBoard = parseBoardId(request, payload);
      if (parsedBoard.response) return parsedBoard.response;

      const board = authorization.data.boards.find(
        (candidate) => candidate.id === parsedBoard.boardId,
      );
      if (!board) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Board not found');
      }
      if (board.projectId !== projectId) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Board must belong to the requested Project',
        );
      }

      const preference = repository.transact((draft) => {
        const existing = draft.defaultBoardPreferences.find(
          (item) =>
            item.userId === authorization.user.id && item.projectId === projectId,
        );
        if (existing) {
          existing.boardId = board.id;
        } else {
          draft.defaultBoardPreferences.push({
            userId: authorization.user.id,
            projectId,
            boardId: board.id,
          });
        }

        return { projectId, boardId: board.id };
      });

      return HttpResponse.json(preference);
    }),
    http.delete(`${DEFAULT_BOARDS_PATH}/:projectId`, ({ request, params }) => {
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

      const expectedBoardParam = new URL(request.url).searchParams.get('expectedBoardId');
      const expectedBoardId = expectedBoardParam !== null
        ? parsePositiveId(expectedBoardParam)
        : null;
      if (expectedBoardParam !== null && !expectedBoardId) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'A valid expectedBoardId query parameter is required',
        );
      }

      repository.transact((draft) => {
        draft.defaultBoardPreferences = draft.defaultBoardPreferences.filter(
          (preference) =>
            preference.userId !== authorization.user.id ||
            preference.projectId !== projectId ||
            (expectedBoardId != null && preference.boardId !== expectedBoardId),
        );
      });

      return new HttpResponse(null, { status: 204 });
    }),
  ];
}
