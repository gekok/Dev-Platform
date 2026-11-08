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
    http.get('*/api/v1/users', ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const url = new URL(request.url);
      const query = (url.searchParams.get('query') ?? '').trim().toLocaleLowerCase();
      const limit = Number(url.searchParams.get('limit') ?? 20);
      if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'limit must be between 1 and 100');
      }
      const users = authentication.data.users
        .filter((user) => !query ||
          `${user.fullName} ${user.email}`.toLocaleLowerCase().includes(query))
        .slice(0, limit)
        .map(({ id, fullName }) => ({ id, fullName }));
      return HttpResponse.json(users);
    }),
    http.get('*/api/v1/users/profiles', ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;
      const url = new URL(request.url);
      const projectId = parsePositiveId(url.searchParams.get('projectId'));
      if (!projectId) {
        return errorResponse(request, 400, 'BAD_REQUEST', 'projectId must be a positive number');
      }
      const authorization = authorizeProject(
        request,
        repository,
        resolveUserFromToken,
        projectId,
      );
      if (authorization.response) return authorization.response;
      const ids = new Set(url.searchParams.getAll('ids'));
      return HttpResponse.json(authorization.data.users
        .filter((user) => ids.has(user.id))
        .map((user) => ({ id: user.id, displayName: user.fullName })));
    }),
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
    http.post('*/api/v1/projects/:projectId/invitations', async ({ request, params }) => {
      const projectId = parsePositiveId(params.projectId);
      if (!projectId) return errorResponse(request, 404, 'NOT_FOUND', 'Project not found');
      const authorization = authorizeProject(
        request,
        repository,
        resolveUserFromToken,
        projectId,
      );
      if (authorization.response) return authorization.response;
      if (authorization.membership.role !== 'OWNER') {
        return errorResponse(request, 403, 'FORBIDDEN', 'Project Owner access is required');
      }

      const payload = await readJson(request);
      const inviteeId = typeof payload?.userId === 'string' ? payload.userId : '';
      const invitee = authorization.data.users.find((user) => user.id === inviteeId);
      if (!invitee) {
        return errorResponse(request, 422, 'INVITEE_NOT_AVAILABLE', 'Invitee account is unavailable');
      }
      if (inviteeId === authorization.user.id || authorization.data.projectMembers.some(
        (membership) => membership.projectId === projectId && membership.userId === inviteeId,
      )) {
        return errorResponse(request, 409, 'MEMBERSHIP_ALREADY_EXISTS', 'Account is already a Project member');
      }
      if (authorization.data.invitations.some((invitation) =>
        invitation.projectId === projectId && invitation.userId === inviteeId &&
        invitation.status === 'PENDING')) {
        return errorResponse(request, 409, 'INVITATION_ALREADY_PENDING', 'A pending invitation already exists');
      }

      const invitation = repository.transact((draft, allocateId) => {
        const created = {
          id: allocateId('invitation'),
          projectId,
          userId: inviteeId,
          invitedByUserId: authorization.user.id,
          status: 'PENDING',
          createdAt: new Date().toISOString(),
          respondedAt: null,
        };
        draft.invitations.push(created);
        return created;
      });
      return HttpResponse.json({
        ...invitation,
        projectName: authorization.project.name,
        inviteeName: invitee.fullName,
      }, { status: 201 });
    }),
    http.get('*/api/v1/projects/:projectId/invitations', ({ request, params }) => {
      const projectId = parsePositiveId(params.projectId);
      if (!projectId) return errorResponse(request, 404, 'NOT_FOUND', 'Project not found');
      const authorization = authorizeProject(
        request,
        repository,
        resolveUserFromToken,
        projectId,
      );
      if (authorization.response) return authorization.response;
      if (authorization.membership.role !== 'OWNER') {
        return errorResponse(request, 403, 'FORBIDDEN', 'Project Owner access is required');
      }
      const status = new URL(request.url).searchParams.get('status');
      const invitations = authorization.data.invitations
        .filter((invitation) => invitation.projectId === projectId &&
          (!status || invitation.status === status))
        .map((invitation) => ({
          ...invitation,
          projectName: authorization.project.name,
          inviteeName: authorization.data.users.find((user) => user.id === invitation.userId)?.fullName,
        }));
      return HttpResponse.json(invitations);
    }),
    http.post('*/api/v1/projects/:projectId/invitations/:invitationId/cancel', ({ request, params }) => {
      const projectId = parsePositiveId(params.projectId);
      const invitationId = parsePositiveId(params.invitationId);
      if (!projectId || !invitationId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Invitation not found');
      }
      const authorization = authorizeProject(
        request,
        repository,
        resolveUserFromToken,
        projectId,
      );
      if (authorization.response) return authorization.response;
      if (authorization.membership.role !== 'OWNER') {
        return errorResponse(request, 403, 'FORBIDDEN', 'Project Owner access is required');
      }
      const invitation = repository.transact((draft) => {
        const target = draft.invitations.find((item) =>
          item.id === invitationId && item.projectId === projectId && item.status === 'PENDING');
        if (!target) return null;
        target.status = 'CANCELLED';
        target.respondedAt = new Date().toISOString();
        return target;
      });
      if (!invitation) return errorResponse(request, 404, 'NOT_FOUND', 'Invitation not found');
      return HttpResponse.json(invitation);
    }),
    http.get('*/api/v1/invitations/me', ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;
      const status = new URL(request.url).searchParams.get('status') ?? 'PENDING';
      const invitations = authentication.data.invitations
        .filter((invitation) => invitation.userId === authentication.user.id &&
          (!status || invitation.status === status))
        .map((invitation) => ({
          ...invitation,
          projectName: authentication.data.projects.find(
            (project) => project.id === invitation.projectId,
          )?.name ?? null,
        }));
      return HttpResponse.json(invitations);
    }),
    http.post('*/api/v1/invitations/:invitationId/:decision', ({ request, params }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;
      const invitationId = parsePositiveId(params.invitationId);
      const nextStatus = params.decision === 'accept'
        ? 'ACCEPTED'
        : params.decision === 'reject'
          ? 'REJECTED'
          : null;
      if (!invitationId || !nextStatus) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Invitation not found');
      }
      const response = repository.transact((draft) => {
        const invitation = draft.invitations.find((item) =>
          item.id === invitationId && item.userId === authentication.user.id &&
          item.status === 'PENDING');
        if (!invitation) return null;
        invitation.status = nextStatus;
        invitation.respondedAt = new Date().toISOString();
        if (nextStatus === 'ACCEPTED') {
          draft.projectMembers.push({
            projectId: invitation.projectId,
            userId: authentication.user.id,
            role: 'MEMBER',
          });
        }
        return invitation;
      });
      if (!response) return errorResponse(request, 404, 'NOT_FOUND', 'Invitation not found');
      if (nextStatus === 'ACCEPTED') {
        return HttpResponse.json({
          userId: authentication.user.id,
          role: 'MEMBER',
          joinedAt: response.respondedAt,
        });
      }
      return HttpResponse.json(response);
    }),
  ];
}
