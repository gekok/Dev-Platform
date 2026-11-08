import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import {
  listMyNotifications,
  markNotificationRead,
} from '../services/notificationApi.js';

export function useNotifications(userId, { unreadOnly = false } = {}) {
  return useQuery({
    queryKey: queryKeys.notifications(userId, { unreadOnly }),
    queryFn: () => listMyNotifications({ unreadOnly }),
    enabled: Boolean(userId),
  });
}

export function useMarkNotificationRead(userId) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: markNotificationRead,
    onSuccess: () => queryClient.invalidateQueries({
      queryKey: queryKeys.notifications(userId),
    }),
  });
}
