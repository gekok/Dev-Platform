import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  isAccessTokenStorageChange,
  SESSION_EXPIRED_EVENT,
} from '../../features/auth/session.js';
import { isRetryableApiError } from '../../shared/services/apiErrors.js';

export default function AppProviders({ children }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (failureCount, error) => failureCount < 1 && isRetryableApiError(error),
            refetchOnWindowFocus: false,
            staleTime: 30_000,
          },
        },
      }),
  );

  useEffect(() => {
    function clearSessionQueries() {
      queryClient.clear();
    }
    function clearQueriesAfterStorageChange(event) {
      if (isAccessTokenStorageChange(event)) queryClient.clear();
    }

    window.addEventListener(SESSION_EXPIRED_EVENT, clearSessionQueries);
    window.addEventListener('storage', clearQueriesAfterStorageChange);
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, clearSessionQueries);
      window.removeEventListener('storage', clearQueriesAfterStorageChange);
    };
  }, [queryClient]);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
