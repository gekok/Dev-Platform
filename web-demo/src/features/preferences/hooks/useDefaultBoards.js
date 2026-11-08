import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import {
  clearDefaultBoard,
  listDefaultBoards,
  setDefaultBoard,
} from '../services/preferenceApi.js';

export function isDefaultBoardPreferenceEnabled(userId, mode) {
  return Boolean(userId) && mode === 'demo';
}

export function useDefaultBoards(userId) {
  return useQuery({
    queryKey: queryKeys.defaultBoards(userId),
    queryFn: listDefaultBoards,
    enabled: isDefaultBoardPreferenceEnabled(userId, import.meta.env.VITE_APP_MODE),
  });
}

export function useSetDefaultBoard(userId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ projectId, boardId }) => setDefaultBoard(projectId, boardId),
    onSuccess: () =>
      userId
        ? queryClient.invalidateQueries({
            queryKey: queryKeys.defaultBoards(userId),
          })
        : undefined,
  });
}

export function useClearDefaultBoard(userId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ projectId, expectedBoardId }) =>
      clearDefaultBoard(projectId, expectedBoardId),
    onSuccess: () =>
      userId
        ? queryClient.invalidateQueries({
            queryKey: queryKeys.defaultBoards(userId),
          })
        : undefined,
  });
}
