import { useState } from 'react';
import { NavLink, Outlet, useMatches, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from '../../features/auth/hooks/useCurrentUser.js';
import { clearSession } from '../../features/auth/session.js';
import { useProject, useProjects } from '../../features/projects/hooks/useProjects.js';
import NotificationPanel from '../../features/notifications/components/NotificationPanel.jsx';
import '../../features/notifications/notifications.css';
import { resetDemoData } from '../../mocks/demoControls.js';
import AccountMenu from './AccountMenu.jsx';

function getActiveRouteParams(matches) {
  return matches.reduce((params, match) => ({ ...params, ...match.params }), {});
}

export default function AppLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const routeParams = getActiveRouteParams(useMatches());
  const { projectId, boardId } = routeParams;
  const { data: user } = useCurrentUser();
  const projectsQuery = useProjects();
  const projectQuery = useProject(projectId);
  const [resetError, setResetError] = useState(null);

  function handleSignOut() {
    clearSession();
    queryClient.clear();
    navigate('/login', { replace: true });
  }

  function handleResetDemoData() {
    const shouldReset = window.confirm(
      'Reset demo data? New Projects, Boards, Cards, Comments, and Notifications will be removed.',
    );
    if (!shouldReset) return;

    try {
      resetDemoData();
      queryClient.clear();
      window.location.assign('/projects');
    } catch {
      setResetError('Demo data could not be reset. Check browser storage and try again.');
    }
  }

  function handleWorkspaceChange(event) {
    const nextProjectId = event.target.value;
    navigate(nextProjectId ? `/projects/${nextProjectId}` : '/projects');
  }

  const projects = projectsQuery.data ?? [];
  const project = projectQuery.data ?? projects.find(
    (candidate) => candidate.id === Number(projectId),
  );

  return (
    <div className={boardId ? 'app-shell app-shell-board' : 'app-shell'}>
      <div className={boardId ? 'workspace workspace-board' : 'workspace'}>
        <header className="global-nav" aria-label="Account and workspace navigation">
          <div className="global-nav-main">
            <NavLink className="brand" to="/projects" aria-label="Dev Platform home">
              <span className="brand-mark">D</span>
              <span className="brand-name">
                <strong>devplatform</strong>
                <small>PROJECT WORKSPACE</small>
              </span>
            </NavLink>

            <label className="workspace-switcher">
              <span className="workspace-switcher-label">Workspace</span>
              <select
                aria-label="Switch project workspace"
                onChange={handleWorkspaceChange}
                value={projectId ?? ''}
              >
                <option value="">All projects</option>
                {projects.map((item) => (
                  <option key={item.id} value={item.id}>{item.name}</option>
                ))}
              </select>
              <span className="workspace-switcher-chevron" aria-hidden="true">⌄</span>
            </label>
          </div>

          <div className="global-nav-actions">
            <span className="demo-status"><i /> Demo environment</span>
            <div className="global-nav-tools">
              <button
                aria-label="Reset demo data"
                className="icon-button reset-demo-button"
                onClick={handleResetDemoData}
                title="Reset demo data"
                type="button"
              >
                <span aria-hidden="true">↻</span>
              </button>
              <NotificationPanel userId={user?.id} />
            </div>
            <AccountMenu onSignOut={handleSignOut} user={user} />
          </div>
        </header>

        {project && (
          <div
            aria-label={`${project.name} project context`}
            className="project-nav"
            role="group"
          >
            <div className="project-nav-identity">
              <span className="project-nav-mark" aria-hidden="true">
                {project.name.slice(0, 1).toUpperCase()}
              </span>
              <span className="project-nav-name">
                <strong>{project.name}</strong>
                {project.role && !boardId && (
                  <small>{project.role === 'OWNER' ? 'Owner' : 'Member'}</small>
                )}
              </span>
            </div>
          </div>
        )}

        <main className={boardId ? 'page-content page-content-board' : 'page-content'}>
          {resetError && <p className="form-inline-error" role="alert">{resetError}</p>}
          <Outlet />
        </main>
      </div>
    </div>
  );
}
