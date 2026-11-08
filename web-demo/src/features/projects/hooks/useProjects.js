import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  cancelProjectInvitation,
  createProjectInvitation,
  getProject,
  listMyProjectInvitations,
  listProjectInvitations,
  listProjectMembers,
  listProjects,
  respondToProjectInvitation,
  searchProjectInvitees,
} from '../services/projectApi.js';
import { queryKeys } from '../../../shared/query/queryKeys.js';

export function useProjects() {
  return useQuery({ queryKey: queryKeys.projects, queryFn: listProjects });
}

export function useProject(projectId) {
  return useQuery({
    queryKey: queryKeys.project(projectId),
    queryFn: () => getProject(projectId),
    enabled: Boolean(projectId),
  });
}

export function useProjectMembers(projectId, { enabled = true } = {}) {
  return useQuery({
    queryKey: queryKeys.projectMembers(projectId),
    queryFn: () => listProjectMembers(projectId),
    enabled: Boolean(projectId) && enabled,
  });
}

export function useProjectInvitations(projectId, { enabled = true, status = 'PENDING' } = {}) {
  return useQuery({
    queryKey: queryKeys.projectInvitations(projectId, status),
    queryFn: () => listProjectInvitations(projectId, status),
    enabled: Boolean(projectId) && enabled,
  });
}

export function useMyProjectInvitations(userId, status = 'PENDING') {
  return useQuery({
    queryKey: queryKeys.myProjectInvitations(userId, status),
    queryFn: () => listMyProjectInvitations(status),
    enabled: Boolean(userId),
  });
}

export function useProjectInviteeSearch(query, enabled = true) {
  const normalizedQuery = query.trim();
  return useQuery({
    queryKey: queryKeys.projectInviteeSearch(normalizedQuery),
    queryFn: () => searchProjectInvitees(normalizedQuery),
    enabled: enabled && normalizedQuery.length >= 2,
    staleTime: 30_000,
  });
}

export function useCreateProjectInvitation(projectId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId) => createProjectInvitation(projectId, userId),
    onSuccess: async () => Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.projectInvitations(projectId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.projectMembers(projectId) }),
    ]),
  });
}

export function useCancelProjectInvitation(projectId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (invitationId) => cancelProjectInvitation(projectId, invitationId),
    onSuccess: () => queryClient.invalidateQueries({
      queryKey: queryKeys.projectInvitations(projectId),
    }),
  });
}

export function useRespondToProjectInvitation(userId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ invitationId, decision }) =>
      respondToProjectInvitation(invitationId, decision),
    onSuccess: async () => Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.myProjectInvitations(userId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.projects }),
      queryClient.invalidateQueries({ queryKey: ['projectMembers'] }),
    ]),
  });
}
