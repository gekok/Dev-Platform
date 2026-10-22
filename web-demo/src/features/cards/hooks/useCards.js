import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import {
  addComment,
  assignCard,
  createCard,
  getCard,
  listCards,
  listComments,
  moveCard,
} from '../services/cardApi.js';
import { normalizeCardId } from '../cardIds.js';

const NOTIFICATIONS_QUERY_PREFIX = ['notifications'];

export function useCards(boardId) {
  return useQuery({
    queryKey: queryKeys.cards(boardId),
    queryFn: () => listCards(boardId),
    enabled: Boolean(boardId),
  });
}

export function useCard(cardId) {
  const normalizedCardId = normalizeCardId(cardId);
  return useQuery({
    queryKey: queryKeys.card(normalizedCardId),
    queryFn: () => getCard(normalizedCardId),
    enabled: Boolean(cardId),
  });
}

export function useCardComments(cardId) {
  const normalizedCardId = normalizeCardId(cardId);
  return useQuery({
    queryKey: queryKeys.comments(normalizedCardId),
    queryFn: () => listComments(normalizedCardId),
    enabled: Boolean(cardId),
  });
}

export function useCreateCard(boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload) => createCard(boardId, payload),
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.cards(boardId) }),
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
    ]),
  });
}

export function useMoveCard(boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ cardId, toColumnId, toPosition }) =>
      moveCard(normalizeCardId(cardId), { toColumnId, toPosition }),
    onSuccess: async (_card, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.cards(boardId) }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.card(normalizeCardId(variables.cardId)),
        }),
        queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
      ]);
    },
  });
}

export function useAssignCard(boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ cardId, assigneeId }) =>
      assignCard(normalizeCardId(cardId), { assigneeId }),
    onSuccess: async (_card, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.cards(boardId) }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.card(normalizeCardId(variables.cardId)),
        }),
        queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
      ]);
    },
  });
}

export function useAddComment(boardId, cardId) {
  const queryClient = useQueryClient();
  const normalizedCardId = normalizeCardId(cardId);

  return useMutation({
    mutationFn: (payload) => addComment(normalizedCardId, payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.comments(normalizedCardId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.cards(boardId) }),
        queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
      ]);
    },
  });
}
