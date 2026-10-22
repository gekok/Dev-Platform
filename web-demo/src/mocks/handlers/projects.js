import { http, HttpResponse } from 'msw';
import {
  authenticateRequest,
  authorizeProject,
  errorResponse,
  parsePositiveId,
  readJson,
} from './utils.js';

function mapProject(project, membership, boards) {
  return {
    id: project.id,
    name: project.name,
    ownerId: project.ownerId,
    role: membership.role,
    createdAt: project.createdAt,
    boardCount: boards.filter((board) => board.projectId === project.id).length,
  };
}

export function createProjectHandlers(repository, resolveUserFromToken) {
  return [
    http.get('*/api/v1/projects', ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const memberships = authentication.data.projectMembers.filter(
        (membership) => membership.userId === authentication.user.id,
      );
      const projects = memberships
        .map((membership) => {
          const project = authentication.data.projects.find(
            (candidate) => candidate.id === membership.projectId,
          );
          return project
            ? mapProject(project, membership, authentication.data.boards)
            : null;
        })
        .filter(Boolean)
        .sort((left, right) => right.createdAt.localeCompare(left.createdAt));

      return HttpResponse.json(projects);
    }),
    http.post('*/api/v1/projects', async ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const payload = await readJson(request);
      const name = typeof payload?.name === 'string' ? payload.name.trim() : '';
      if (!name || name.length > 100) {
        return errorResponse(
          request,
          400,
          'BAD_REQUEST',
          'Project name must contain 1 to 100 characters',
        );
      }

      const createdAt = new Date().toISOString();
      const created = repository.transact((draft, allocateId) => {
        const project = {
          id: allocateId('project'),
          name,
          ownerId: authentication.user.id,
          createdAt,
        };
        draft.projects.push(project);
        draft.projectMembers.push({
          projectId: project.id,
          userId: authentication.user.id,
          role: 'OWNER',
        });
        return { ...project, role: 'OWNER' };
      });

      return HttpResponse.json(created, { status: 201 });
    }),
    http.get('*/api/v1/projects/:projectId/members', ({ request, params }) => {
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

      const members = authorization.data.projectMembers
        .filter((membership) => membership.projectId === projectId)
        .map((membership) => {
          const user = authorization.data.users.find(
            (candidate) => candidate.id === membership.userId,
          );
          return user
            ? {
                id: user.id,
                email: user.email,
                fullName: user.fullName,
                role: membership.role,
              }
            : null;
        })
        .filter(Boolean);

      return HttpResponse.json(members);
    }),
    http.get('*/api/v1/projects/:projectId', ({ request, params }) => {
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

      return HttpResponse.json({
        id: authorization.project.id,
        name: authorization.project.name,
        ownerId: authorization.project.ownerId,
        role: authorization.membership.role,
        createdAt: authorization.project.createdAt,
      });
    }),
  ];
}
