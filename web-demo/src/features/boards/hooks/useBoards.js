import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import {
  addColumn,
  createProjectBoard,
  getBoard,
  listProjectBoards,
  reorderColumns,
} from '../services/boardApi.js';

export function useProjectBoards(projectId) {
  return useQuery({
    queryKey: queryKeys.projectBoards(projectId),
    queryFn: () => listProjectBoards(projectId),
    enabled: Boolean(projectId),
  });
}

export function useBoard(boardId) {
  return useQuery({
    queryKey: queryKeys.board(boardId),
    queryFn: () => getBoard(boardId),
    enabled: Boolean(boardId),
  });
}

export function useCreateProjectBoard(projectId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload) => createProjectBoard(projectId, payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.projectBoards(projectId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.projects }),
      ]);
    },
  });
}

export function useAddColumn(projectId, boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload) => addColumn(boardId, payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.board(boardId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.projectBoards(projectId) }),
      ]);
    },
  });
}

export function useReorderColumns(projectId, boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderedColumnIds) => reorderColumns(boardId, orderedColumnIds),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.board(boardId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.projectBoards(projectId) }),
      ]);
    },
  });
}
