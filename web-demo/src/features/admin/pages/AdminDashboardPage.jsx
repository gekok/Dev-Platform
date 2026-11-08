import {
  Activity,
  CheckCircle,
  DatabaseCheck,
  Key,
  Lock,
  People,
  ShieldCheck,
} from 'react-bootstrap-icons';
import { useCurrentUser } from '../../auth/hooks/useCurrentUser.js';
import '../admin.css';

const API_ROWS = [
  {
    method: 'POST',
    path: '/api/v1/auth/login',
    purpose: 'Issue the current bearer session',
    status: 'Available in demo',
    ready: true,
  },
  {
    method: 'GET',
    path: '/api/v1/users/me',
    purpose: 'Resolve the signed-in account',
    status: 'Available in demo',
    ready: true,
  },
  {
    method: 'POST',
    path: '/api/v1/admin/accounts',
    purpose: 'Provision a managed account',
    status: 'Backend contract open',
    ready: false,
  },
];

const BOUNDARIES = [
  {
    icon: People,
    title: 'Account lifecycle',
    description: 'Account provisioning remains disabled until the admin contract is approved.',
    state: 'Waiting for API',
  },
  {
    icon: Key,
    title: 'System roles',
    description: 'SYSTEM_ADMIN belongs to user-service and is separate from Project roles.',
    state: 'Boundary defined',
  },
  {
    icon: Lock,
    title: 'Project isolation',
    description: 'Admin access does not grant access to Projects, Boards, Cards, or comments.',
    state: 'Protected by design',
  },
];

export default function AdminDashboardPage() {
  const { data: user } = useCurrentUser();

  return (
    <div className="admin-dashboard">
      <section className="admin-hero">
        <div>
          <span className="admin-eyebrow"><ShieldCheck /> SYSTEM ADMIN</span>
          <h1>System control, without crossing service boundaries.</h1>
          <p>
            Review the administration surface and backend readiness while keeping
            Project workspaces isolated from system-level privileges.
          </p>
        </div>
        <div className="admin-identity-card">
          <span className="admin-identity-icon"><ShieldCheck /></span>
          <div>
            <small>Signed in as</small>
            <strong>{user?.fullName}</strong>
            <span>{user?.email}</span>
          </div>
          <span className="admin-role-badge">SYSTEM_ADMIN</span>
        </div>
      </section>

      <section className="admin-metrics" aria-label="Administration overview">
        <article>
          <span className="admin-metric-icon"><Activity /></span>
          <div><strong>4</strong><span>Owned backend services</span></div>
        </article>
        <article>
          <span className="admin-metric-icon"><CheckCircle /></span>
          <div><strong>2</strong><span>Demo auth endpoints active</span></div>
        </article>
        <article>
          <span className="admin-metric-icon"><Lock /></span>
          <div><strong>0</strong><span>Project privileges from admin role</span></div>
        </article>
      </section>

      <div className="admin-grid">
        <section className="admin-panel admin-panel-wide" aria-labelledby="api-readiness-title">
          <div className="admin-panel-heading">
            <div>
              <span className="admin-panel-kicker">Integration surface</span>
              <h2 id="api-readiness-title">API readiness</h2>
            </div>
            <span className="admin-panel-icon"><DatabaseCheck /></span>
          </div>

          <div className="admin-api-table">
            {API_ROWS.map((row) => (
              <article className="admin-api-row" key={`${row.method}-${row.path}`}>
                <span className={`admin-method admin-method-${row.method.toLowerCase()}`}>
                  {row.method}
                </span>
                <code>{row.path}</code>
                <p>{row.purpose}</p>
                <span className={row.ready ? 'admin-api-state is-ready' : 'admin-api-state'}>
                  {row.status}
                </span>
              </article>
            ))}
          </div>
        </section>

        <section className="admin-panel" aria-labelledby="access-boundary-title">
          <div className="admin-panel-heading">
            <div>
              <span className="admin-panel-kicker">Least privilege</span>
              <h2 id="access-boundary-title">Access boundary</h2>
            </div>
          </div>

          <div className="admin-boundary-list">
            {BOUNDARIES.map(({ icon: Icon, title, description, state }) => (
              <article key={title}>
                <span className="admin-boundary-icon"><Icon /></span>
                <div>
                  <strong>{title}</strong>
                  <p>{description}</p>
                  <small>{state}</small>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>

      <aside className="admin-notice">
        <ShieldCheck aria-hidden="true" />
        <div>
          <strong>Safe scaffold</strong>
          <p>
            This console does not call unapproved account-management APIs. Actions can
            be connected after the user-service contract and Gateway role enforcement are complete.
          </p>
        </div>
      </aside>
    </div>
  );
}
