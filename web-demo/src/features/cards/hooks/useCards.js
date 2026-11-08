import { useMemo } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
import { flattenCardPages, hasDuplicateCardPages } from '../cardPageUtils.js';

const NOTIFICATIONS_QUERY_PREFIX = ['notifications'];
const EMPTY_CARD_PAGES = [];

export function useCards(projectId, boardId, columnId) {
  const query = useInfiniteQuery({
    queryKey: queryKeys.cards(projectId, boardId, columnId),
    queryFn: ({ pageParam }) => listCards(projectId, boardId, columnId, {
      limit: 5,
      cursor: pageParam,
    }),
    initialPageParam: null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: Boolean(projectId && boardId && columnId),
  });

  const pages = query.data?.pages ?? EMPTY_CARD_PAGES;
  const cards = useMemo(() => flattenCardPages(pages), [pages]);
  const hasDuplicateCards = useMemo(() => hasDuplicateCardPages(pages), [pages]);

  return {
    ...query,
    cards,
    hasDuplicateCards,
  };
}

export function useCard(projectId, cardId) {
  const normalizedCardId = normalizeCardId(cardId);
  return useQuery({
    queryKey: queryKeys.card(projectId, normalizedCardId),
    queryFn: () => getCard(projectId, normalizedCardId),
    enabled: Boolean(cardId),
  });
}

export function useCardComments(projectId, cardId) {
  const normalizedCardId = normalizeCardId(cardId);
  return useQuery({
    queryKey: queryKeys.comments(projectId, normalizedCardId),
    queryFn: () => listComments(projectId, normalizedCardId),
    enabled: Boolean(cardId),
  });
}

export function useCreateCard(projectId, boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload) => createCard(projectId, boardId, payload),
    onSuccess: () => Promise.all([
      queryClient.resetQueries({ queryKey: queryKeys.cards(projectId, boardId) }),
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
    ]),
  });
}

export function useMoveCard(projectId, boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ cardId, toColumnId, toPosition }) =>
      moveCard(projectId, normalizeCardId(cardId), { toColumnId, toPosition }),
    onSuccess: async (_card, variables) => {
      await Promise.all([
        queryClient.resetQueries({ queryKey: queryKeys.cards(projectId, boardId) }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.card(projectId, normalizeCardId(variables.cardId)),
        }),
        queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
      ]);
    },
  });
}

export function useAssignCard(projectId, boardId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ cardId, assigneeId }) =>
      assignCard(projectId, normalizeCardId(cardId), { assigneeId }),
    onSuccess: async (_card, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.cards(projectId, boardId) }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.card(projectId, normalizeCardId(variables.cardId)),
        }),
        queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
      ]);
    },
  });
}

export function useAddComment(projectId, boardId, cardId) {
  const queryClient = useQueryClient();
  const normalizedCardId = normalizeCardId(cardId);

  return useMutation({
    mutationFn: (payload) => addComment(projectId, normalizedCardId, payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: queryKeys.comments(projectId, normalizedCardId),
        }),
        queryClient.invalidateQueries({ queryKey: queryKeys.cards(projectId, boardId) }),
        queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_PREFIX }),
      ]);
    },
  });
}
