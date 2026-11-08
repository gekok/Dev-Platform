import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, CheckCircle, Kanban, ShieldCheck } from 'react-bootstrap-icons';
import { getCurrentUser, login } from '../services/authApi.js';
import { useCurrentUser } from '../hooks/useCurrentUser.js';
import {
  clearSession,
  getAccessToken,
  SESSION_EXPIRED_EVENT,
  setAccessToken,
} from '../session.js';
import { isSystemAdmin } from '../roles.js';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import { getApiErrorMessage } from '../../../shared/services/apiErrors.js';

const isDemoMode = import.meta.env.VITE_APP_MODE === 'demo';

function getErrorMessage(error) {
  const fallback = error instanceof Error && !error.isAxiosError
    ? error.message
    : 'Unable to sign in. Please try again.';
  return getApiErrorMessage(error, fallback);
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [demoAccounts, setDemoAccounts] = useState(null);
  const [sessionExpired, setSessionExpired] = useState(Boolean(location.state?.sessionExpired));
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const currentUserQuery = useCurrentUser();
  const loginMutation = useMutation({
    mutationFn: async (credentials) => {
      const session = await login(credentials);
      setAccessToken(session.accessToken);
      const user = await getCurrentUser();
      return { session, user };
    },
    onSuccess: ({ user }) => {
      queryClient.removeQueries();
      queryClient.setQueryData(queryKeys.currentUser, user);
      const requestedPath = location.state?.from;
      const landingPath = isSystemAdmin(user) ? '/admin' : '/projects';
      navigate(requestedPath ?? landingPath, { replace: true });
    },
    onError: () => {
      clearSession();
      queryClient.removeQueries();
    },
  });

  useEffect(() => {
    function handleSessionExpired() {
      setSessionExpired(true);
    }

    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, []);

  useEffect(() => {
    if (!isDemoMode) return undefined;

    let active = true;
    import('../../../mocks/data/seed.js').then((seed) => {
      if (active) {
        setDemoAccounts({
          owner: seed.DEMO_CREDENTIALS,
          admin: seed.DEMO_ADMIN_CREDENTIALS,
        });
      }
    });

    return () => {
      active = false;
    };
  }, []);

  if (getAccessToken() && currentUserQuery.data) {
    return (
      <Navigate
        to={isSystemAdmin(currentUserQuery.data) ? '/admin' : '/projects'}
        replace
      />
    );
  }

  function handleSubmit(event) {
    event.preventDefault();
    clearSession();
    queryClient.removeQueries();
    loginMutation.mutate({ email: email.trim(), password });
  }

  function selectDemoAccount(credentials) {
    setEmail(credentials.email);
    setPassword(credentials.password);
    loginMutation.reset();
  }

  return (
    <main className="login-page">
      <section className="login-showcase" aria-labelledby="login-showcase-title">
        <div className="login-showcase-brand">
          <span className="login-showcase-mark">D</span>
          <span>
            <strong>devplatform</strong>
            <small>PROJECT WORKSPACE</small>
          </span>
        </div>

        <div className="login-showcase-copy">
          <span className="eyebrow">PROJECT-FIRST DELIVERY</span>
          <h1 id="login-showcase-title">One workspace for focused teams.</h1>
          <p>
            Plan work, coordinate Boards, and keep every Card connected to the
            Project that owns it.
          </p>
        </div>

        <div className="login-feature-list" aria-label="Platform capabilities">
          <span><CheckCircle /> Project-scoped collaboration</span>
          <span><CheckCircle /> Kanban delivery flow</span>
          <span><CheckCircle /> Separate system administration</span>
        </div>

        <div className="login-showcase-orbit" aria-hidden="true">
          <span><Kanban /></span>
          <span><ShieldCheck /></span>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card" aria-labelledby="login-title">
          <div className="login-card-heading">
            <span className="login-mobile-mark" aria-hidden="true">D</span>
            <span className="eyebrow">SECURE ACCESS</span>
            <h2 id="login-title">Welcome back</h2>
            <p className="login-intro">
              Sign in to continue to your workspace or administration console.
            </p>
          </div>

          {sessionExpired && (
            <div className="form-alert" role="alert">
              Your session expired. Sign in again to continue.
            </div>
          )}
          {currentUserQuery.isError && !sessionExpired && (
            <div className="form-alert" role="alert">
              {getApiErrorMessage(
                currentUserQuery.error,
                'Your saved session could not be verified. Sign in to continue.',
              )}
            </div>
          )}
          {loginMutation.isError && (
            <div className="form-alert" role="alert">
              {getErrorMessage(loginMutation.error)}
            </div>
          )}

          <form className="login-form" onSubmit={handleSubmit}>
            <label htmlFor="login-email">Email</label>
            <input
              id="login-email"
              autoComplete="username"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@company.com"
              required
            />

            <label htmlFor="login-password">Password</label>
            <input
              id="login-password"
              autoComplete="current-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />

            <button className="primary-button login-submit" type="submit" disabled={loginMutation.isPending}>
              <span>{loginMutation.isPending ? 'Signing in…' : 'Sign in'}</span>
              {!loginMutation.isPending && <ArrowRight aria-hidden="true" />}
            </button>
          </form>

          {isDemoMode && demoAccounts && (
            <div className="demo-account-section">
              <div className="demo-account-heading">
                <span>Demo access</span>
                <small>Choose an account to fill the form</small>
              </div>
              <div className="demo-account-grid">
                <button type="button" onClick={() => selectDemoAccount(demoAccounts.owner)}>
                  <span className="demo-account-icon"><Kanban /></span>
                  <span><strong>Workspace owner</strong><small>{demoAccounts.owner.email}</small></span>
                </button>
                <button type="button" onClick={() => selectDemoAccount(demoAccounts.admin)}>
                  <span className="demo-account-icon"><ShieldCheck /></span>
                  <span><strong>System admin</strong><small>{demoAccounts.admin.email}</small></span>
                </button>
              </div>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
