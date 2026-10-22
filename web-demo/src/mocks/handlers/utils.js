import { HttpResponse } from 'msw';

export function errorResponse(request, status, code, message) {
  return HttpResponse.json(
    {
      code,
      message,
      timestamp: new Date().toISOString(),
      path: new URL(request.url).pathname,
    },
    { status },
  );
}

export function readJson(request) {
  return request.json().catch(() => null);
}

export function authenticateRequest(request, repository, resolveUserFromToken) {
  const authorization = request.headers.get('authorization') ?? '';
  const match = /^Bearer\s+([^\s]+)$/i.exec(authorization);
  const userId = match ? resolveUserFromToken(match[1]) : null;
  const data = repository.read();
  const user = data.users.find((candidate) => candidate.id === userId);

  if (!user) {
    return {
      response: errorResponse(
        request,
        401,
        'UNAUTHORIZED',
        'A valid bearer token is required',
      ),
    };
  }

  return { data, user };
}

export function authorizeProject(request, repository, resolveUserFromToken, projectId) {
  const authentication = authenticateRequest(request, repository, resolveUserFromToken);
  if (authentication.response) return authentication;

  const project = authentication.data.projects.find(
    (candidate) => candidate.id === projectId,
  );
  if (!project) {
    return {
      response: errorResponse(request, 404, 'NOT_FOUND', 'Project not found'),
    };
  }

  const membership = authentication.data.projectMembers.find(
    (candidate) =>
      candidate.projectId === projectId && candidate.userId === authentication.user.id,
  );
  if (!membership) {
    return {
      response: errorResponse(request, 403, 'FORBIDDEN', 'Project access is forbidden'),
    };
  }

  return { ...authentication, membership, project };
}

export function parsePositiveId(value) {
  if (!/^\d+$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}
