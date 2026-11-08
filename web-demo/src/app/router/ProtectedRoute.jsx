import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useCurrentUser } from '../../features/auth/hooks/useCurrentUser.js';
import {
  getAccessToken,
  isAccessTokenStorageChange,
  SESSION_EXPIRED_EVENT,
} from '../../features/auth/session.js';
import { getApiErrorMessage, getApiErrorStatus } from '../../shared/services/apiErrors.js';

export default function ProtectedRoute() {
  const location = useLocation();
  const [sessionExpired, setSessionExpired] = useState(false);
  const [sessionChanged, setSessionChanged] = useState(false);
  const token = getAccessToken();
  const { isPending, isError, error, refetch } = useCurrentUser();

  useEffect(() => {
    function handleSessionExpired() {
      setSessionExpired(true);
    }
    function handleStorage(event) {
      if (isAccessTokenStorageChange(event)) setSessionChanged(true);
    }

    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    window.addEventListener('storage', handleStorage);
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  if (isError && getApiErrorStatus(error) === 401) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname, sessionExpired: true }}
      />
    );
  }

  if (sessionExpired) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname, sessionExpired: true }}
      />
    );
  }

  if (sessionChanged) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname, sessionExpired: !token }}
      />
    );
  }

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
        Checking your session…
      </main>
    );
  }

  if (isError) {
    return (
      <main className="route-state" role="alert">
        <div>
          <p>{getApiErrorStatus(error) === 403
            ? 'Your account does not have permission to access this workspace.'
            : getApiErrorMessage(error, 'Could not verify your session.')}</p>
          <button className="secondary-button" onClick={() => refetch()} type="button">
            Try again
          </button>
        </div>
      </main>
    );
  }

  return <Outlet />;
}
