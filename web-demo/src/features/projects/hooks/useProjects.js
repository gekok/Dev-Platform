import { useQuery } from '@tanstack/react-query';
import {
  getProject,
  listProjectMembers,
  listProjects,
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

export function useProjectMembers(projectId) {
  return useQuery({
    queryKey: queryKeys.projectMembers(projectId),
    queryFn: () => listProjectMembers(projectId),
    enabled: Boolean(projectId),
  });
}
