import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import {
  clearDefaultBoard,
  listDefaultBoards,
  setDefaultBoard,
} from '../services/preferenceApi.js';

export function useDefaultBoards(userId) {
  return useQuery({
    queryKey: queryKeys.defaultBoards(userId),
    queryFn: listDefaultBoards,
    enabled: Boolean(userId),
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
