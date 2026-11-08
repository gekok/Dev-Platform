import api from '../../../shared/services/axios.js';
import {
  listAllApiPages,
  unwrapApiResponse,
} from '../../../shared/services/apiResponses.js';

function normalizeProject(project) {
  if (!project || typeof project !== 'object') return project;
  return {
    ...project,
    role: project.role ?? project.myRole,
  };
}

function normalizeInvitation(invitation) {
  if (!invitation || typeof invitation !== 'object') return invitation;
  return {
    ...invitation,
    userId: invitation.userId ?? invitation.inviteeUserId,
  };
}

async function listProjectProfiles(projectId, userIds) {
  const uniqueIds = [...new Set(userIds.filter(Boolean).map(String))];
  const profileGroups = [];

  for (let start = 0; start < uniqueIds.length; start += 100) {
    const params = new URLSearchParams();
    params.set('projectId', String(projectId));
    uniqueIds.slice(start, start + 100).forEach((id) => params.append('ids', id));
    try {
      const response = await api.get(`/users/profiles?${params.toString()}`);
      const profiles = unwrapApiResponse(response.data);
      if (Array.isArray(profiles)) profileGroups.push(...profiles);
    } catch (error) {
      const status = error.response?.status;
      if (status ? status < 500 : !error.isAxiosError) throw error;
    }
  }

  return new Map(profileGroups.map((profile) => [String(profile.id), profile]));
}

export async function listProjects() {
  const page = await listAllApiPages('/projects');
  return { ...page, items: page.items.map(normalizeProject) };
}

export async function getProject(projectId) {
  const response = await api.get(`/projects/${projectId}`);
  return normalizeProject(unwrapApiResponse(response.data));
}

export async function createProject(payload) {
  const response = await api.post('/projects', payload);
  return normalizeProject(unwrapApiResponse(response.data));
}

export async function listProjectMembers(projectId) {
  const page = await listAllApiPages(`/projects/${projectId}/members`);
  const members = page.items;
  const profileById = await listProjectProfiles(
    projectId,
    members.filter((member) => !member.fullName).map((member) => member.userId ?? member.id),
  );

  return {
    ...page,
    items: members.map((member) => {
      const userId = member.userId ?? member.id;
      const profile = profileById.get(String(userId));
      return {
        ...member,
        id: userId,
        userId,
        fullName: member.fullName ?? profile?.displayName ?? 'Project member',
      };
    }),
  };
}

export async function searchProjectInvitees(query, limit = 10) {
  const response = await api.get('/users', { params: { query, limit } });
  const users = unwrapApiResponse(response.data);
  return Array.isArray(users) ? users : [];
}

export async function listProjectInvitations(projectId, status = 'PENDING') {
  const page = await listAllApiPages(`/projects/${projectId}/invitations`, { status });
  const invitations = page.items.map(normalizeInvitation);
  const profileById = await listProjectProfiles(
    projectId,
    invitations
      .filter((invitation) => !invitation.inviteeName)
      .map((invitation) => invitation.userId),
  );
  return {
    ...page,
    items: invitations.map((invitation) => ({
      ...invitation,
      inviteeName: invitation.inviteeName ??
        profileById.get(String(invitation.userId))?.displayName ?? 'Project member',
    })),
  };
}

export async function listMyProjectInvitations(status = 'PENDING') {
  const page = await listAllApiPages('/invitations/me', { status });
  return { ...page, items: page.items.map(normalizeInvitation) };
}

export async function createProjectInvitation(projectId, userId) {
  const response = await api.post(`/projects/${projectId}/invitations`, { userId });
  return normalizeInvitation(unwrapApiResponse(response.data));
}

export async function cancelProjectInvitation(projectId, invitationId) {
  const response = await api.post(
    `/projects/${projectId}/invitations/${invitationId}/cancel`,
  );
  return normalizeInvitation(unwrapApiResponse(response.data));
}

export async function respondToProjectInvitation(invitationId, decision) {
  if (!['accept', 'reject'].includes(decision)) {
    throw new TypeError('Invitation decision must be accept or reject.');
  }
  const response = await api.post(`/invitations/${invitationId}/${decision}`);
  return normalizeInvitation(unwrapApiResponse(response.data));
}
