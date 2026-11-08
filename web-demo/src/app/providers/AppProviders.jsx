import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SESSION_EXPIRED_EVENT } from '../../features/auth/session.js';
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

    window.addEventListener(SESSION_EXPIRED_EVENT, clearSessionQueries);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, clearSessionQueries);
  }, [queryClient]);

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
