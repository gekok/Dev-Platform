import { useQuery } from '@tanstack/react-query';
import { getCurrentUser } from '../services/authApi.js';
import { getAccessToken } from '../session.js';
import { queryKeys } from '../../../shared/query/queryKeys.js';

export function useCurrentUser() {
  return useQuery({
    queryKey: queryKeys.currentUser,
    queryFn: getCurrentUser,
    enabled: Boolean(getAccessToken()),
  });
}
