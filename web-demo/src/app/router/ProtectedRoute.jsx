import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useCurrentUser } from '../../features/auth/hooks/useCurrentUser.js';
import { getAccessToken } from '../../features/auth/session.js';

export default function ProtectedRoute() {
  const location = useLocation();
  const token = getAccessToken();
  const { isPending, isError, error, refetch } = useCurrentUser();

  if (!token) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  if (isPending) {
    return (
      <main className="route-state" aria-live="polite">
        Checking demo session…
      </main>
    );
  }

  if (isError && error.response?.status === 401) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname, sessionExpired: true }}
      />
    );
  }

  if (isError) {
    return (
      <main className="route-state" role="alert">
        <div>
          <p>Could not verify the demo session.</p>
          <button className="secondary-button" onClick={() => refetch()} type="button">
            Try again
          </button>
        </div>
      </main>
    );
  }

  return <Outlet />;
}
