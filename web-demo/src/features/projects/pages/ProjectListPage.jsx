import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import ProjectCreateForm from '../components/ProjectCreateForm.jsx';
import { useProjects } from '../hooks/useProjects.js';
import { createProject } from '../services/projectApi.js';
import { queryKeys } from '../../../shared/query/queryKeys.js';

function getErrorMessage(error) {
  return error.response?.data?.message ?? 'Projects could not be loaded. Try again.';
}

function formatDate(value) {
  return new Date(value).toLocaleDateString('en-US', { dateStyle: 'medium' });
}

export default function ProjectListPage() {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const queryClient = useQueryClient();
  const projectsQuery = useProjects();
  const createMutation = useMutation({
    mutationFn: createProject,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.projects }),
  });

  async function handleCreate(name) {
    await createMutation.mutateAsync({ name });
    setShowCreateForm(false);
  }

  return (
    <section className="projects-page" aria-labelledby="projects-title">
      <div className="page-heading">
        <div>
          <span className="eyebrow">WORKSPACE</span>
          <h1 id="projects-title">Projects</h1>
          <p>Bring your team, boards, and work together in one place.</p>
        </div>
        <button
          className="primary-button"
          aria-controls="project-create-form"
          aria-expanded={showCreateForm}
          disabled={createMutation.isPending}
          onClick={() => {
            createMutation.reset();
            setShowCreateForm((visible) => !visible);
          }}
          type="button"
        >
          <span aria-hidden="true">+</span> New project
        </button>
      </div>

      {showCreateForm && (
        <ProjectCreateForm
          error={createMutation.isError ? getErrorMessage(createMutation.error) : null}
          isCreating={createMutation.isPending}
          onCancel={() => {
            setShowCreateForm(false);
            createMutation.reset();
          }}
          onCreate={handleCreate}
        />
      )}

      <div className="section-heading">
        <div>
          <h2>Your projects</h2>
          <span>{projectsQuery.data?.length ?? 0} projects</span>
        </div>
        <span className="section-caption">Sorted by recently created</span>
      </div>

      {projectsQuery.isPending && (
        <div className="page-state" aria-live="polite">Loading projects…</div>
      )}
      {projectsQuery.isError && (
        <div className="empty-state" role="alert">
          <strong>We couldn’t load your projects.</strong>
          <span>{getErrorMessage(projectsQuery.error)}</span>
          <button className="secondary-button" onClick={() => projectsQuery.refetch()} type="button">
            Try again
          </button>
        </div>
      )}
      {projectsQuery.isSuccess && projectsQuery.data.length === 0 && (
        <div className="empty-state">
          <strong>No projects yet</strong>
          <span>Create your first project to start organizing boards.</span>
        </div>
      )}
      {projectsQuery.isSuccess && projectsQuery.data.length > 0 && (
        <div className="project-grid">
          {projectsQuery.data.map((project, index) => (
            <Link
              className="project-card project-card-link"
              key={project.id}
              to={`/projects/${project.id}`}
            >
              <div className={`project-card-mark project-card-mark-${index % 4}`} aria-hidden="true">
                {project.name.slice(0, 1).toUpperCase()}
              </div>
              <div className="project-card-title-row">
                <h3>{project.name}</h3>
                <span className={`role-chip role-${project.role.toLowerCase()}`}>
                  {project.role === 'OWNER' ? 'Owner' : 'Member'}
                </span>
              </div>
              <p className="project-card-description">
                A shared workspace for planning and tracking your team’s work.
              </p>
              <div className="project-card-meta">
                <span>{project.boardCount} boards</span>
                <span>Created {formatDate(project.createdAt)}</span>
              </div>
              <div className="project-card-footer">
                <span className="avatar avatar-small" aria-hidden="true">
                  {project.name.slice(0, 1).toUpperCase()}
                </span>
                <span>Project workspace</span>
                <span className="project-card-arrow">Open project ↗</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}
