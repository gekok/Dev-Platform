import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, CheckCircle, Kanban, ShieldCheck } from 'react-bootstrap-icons';
import { getCurrentUser, login } from '../services/authApi.js';
import { useCurrentUser } from '../hooks/useCurrentUser.js';
import { clearSession, setAccessToken } from '../session.js';
import { isSystemAdmin } from '../roles.js';
import {
  DEMO_ADMIN_CREDENTIALS,
  DEMO_CREDENTIALS,
} from '../../../mocks/data/seed.js';
import { queryKeys } from '../../../shared/query/queryKeys.js';

function getErrorMessage(error) {
  return error.response?.data?.message ?? 'Unable to sign in. Please try again.';
}

export default function LoginPage() {
  const [email, setEmail] = useState(DEMO_CREDENTIALS.email);
  const [password, setPassword] = useState(DEMO_CREDENTIALS.password);
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
      queryClient.setQueryData(queryKeys.currentUser, user);
      const requestedPath = location.state?.from;
      const landingPath = isSystemAdmin(user) ? '/admin' : '/projects';
      navigate(requestedPath ?? landingPath, { replace: true });
    },
    onError: () => clearSession(),
  });

  if (currentUserQuery.data) {
    return (
      <Navigate
        to={isSystemAdmin(currentUserQuery.data) ? '/admin' : '/projects'}
        replace
      />
    );
  }

  function handleSubmit(event) {
    event.preventDefault();
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

          {location.state?.sessionExpired && (
            <div className="form-alert" role="alert">
              Your demo session expired. Sign in again to continue.
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

          <div className="demo-account-section">
            <div className="demo-account-heading">
              <span>Demo access</span>
              <small>Choose an account to fill the form</small>
            </div>
            <div className="demo-account-grid">
              <button type="button" onClick={() => selectDemoAccount(DEMO_CREDENTIALS)}>
                <span className="demo-account-icon"><Kanban /></span>
                <span><strong>Workspace owner</strong><small>{DEMO_CREDENTIALS.email}</small></span>
              </button>
              <button type="button" onClick={() => selectDemoAccount(DEMO_ADMIN_CREDENTIALS)}>
                <span className="demo-account-icon"><ShieldCheck /></span>
                <span><strong>System admin</strong><small>{DEMO_ADMIN_CREDENTIALS.email}</small></span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
