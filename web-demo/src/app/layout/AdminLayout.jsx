import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { ShieldCheck } from 'react-bootstrap-icons';
import { useCurrentUser } from '../../features/auth/hooks/useCurrentUser.js';
import { clearSession } from '../../features/auth/session.js';
import AccountMenu from './AccountMenu.jsx';

export default function AdminLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();

  function handleSignOut() {
    clearSession();
    queryClient.clear();
    navigate('/login', { replace: true });
  }

  return (
    <div className="admin-shell">
      <header className="admin-topbar">
        <NavLink className="brand admin-brand" to="/admin" aria-label="Dev Platform administration">
          <span className="brand-mark">D</span>
          <span className="brand-name">
            <strong>devplatform</strong>
            <small>SYSTEM ADMINISTRATION</small>
          </span>
        </NavLink>

        <div className="admin-topbar-context" aria-label="Current administration area">
          <ShieldCheck aria-hidden="true" />
          <span>Admin console</span>
        </div>

        <AccountMenu onSignOut={handleSignOut} user={user} />
      </header>

      <main className="admin-content">
        <Outlet />
      </main>
    </div>
  );
}
