import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { login } from '../services/authApi.js';
import { setAccessToken } from '../session.js';
import { DEMO_CREDENTIALS } from '../../../mocks/data/seed.js';

function getErrorMessage(error) {
  return error.response?.data?.message ?? 'Unable to sign in. Please try again.';
}

export default function LoginPage() {
  const [email, setEmail] = useState(DEMO_CREDENTIALS.email);
  const [password, setPassword] = useState(DEMO_CREDENTIALS.password);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: ({ accessToken }) => {
      queryClient.clear();
      setAccessToken(accessToken);
      navigate(location.state?.from ?? '/projects', { replace: true });
    },
  });

  function handleSubmit(event) {
    event.preventDefault();
    loginMutation.mutate({ email: email.trim(), password });
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-brand-mark" aria-hidden="true">D</div>
        <span className="eyebrow">DEV PLATFORM · KANBAN DEMO</span>
        <h1 id="login-title">Welcome back</h1>
        <p className="login-intro">
          Sign in to explore the Project-first workspace.
        </p>

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
            {loginMutation.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <aside className="demo-credentials">
          <strong>Demo account</strong>
          <span>{DEMO_CREDENTIALS.email}</span>
          <span>{DEMO_CREDENTIALS.password}</span>
        </aside>
      </section>
    </main>
  );
}
