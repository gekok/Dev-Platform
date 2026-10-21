import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useCurrentUser } from '../../auth/hooks/useCurrentUser.js';
import BoardCreateDialog from '../../boards/components/BoardCreateDialog.jsx';
import {
  useCreateProjectBoard,
  useProjectBoards,
} from '../../boards/hooks/useBoards.js';
import {
  useClearDefaultBoard,
  useDefaultBoards,
} from '../../preferences/hooks/useDefaultBoards.js';
import '../../boards/boards.css';
import { resolveProjectEntry } from '../projectEntry.js';
import { useProject } from '../hooks/useProjects.js';

function getErrorTitle(error) {
  switch (error.response?.status) {
    case 401:
      return 'Your demo session has expired.';
    case 403:
      return 'Project access denied.';
    case 404:
      return 'Project not found.';
    default:
      return 'We couldn’t open this Project.';
  }
}

function getErrorMessage(error) {
  switch (error.response?.status) {
    case 401:
      return 'Sign in again to continue.';
    case 403:
      return 'You are not a member of this Project.';
    case 404:
      return 'Check the Project link and try again.';
    default:
      return error.response?.data?.message ?? 'The request could not be completed. Try again.';
  }
}

function getPreferenceErrorMessage(error) {
  return error.response?.data?.message ?? 'The saved Board preference could not be cleared.';
}

export default function ProjectEntryPage() {
  const { projectId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [cleanupAttempt, setCleanupAttempt] = useState(null);
  const cleanupAttemptRef = useRef(null);
  const [cleanupRetry, setCleanupRetry] = useState(0);
  const projectQuery = useProject(projectId);
  const boardsQuery = useProjectBoards(projectId);
  const userQuery = useCurrentUser();
  const userId = userQuery.data?.id;
  const defaultBoardsQuery = useDefaultBoards(userId);
  const clearPreferenceMutation = useClearDefaultBoard(userId);
  const clearPreference = clearPreferenceMutation.mutateAsync;
  const resetClearPreference = clearPreferenceMutation.reset;
  const createBoardMutation = useCreateProjectBoard(projectId);
  const projectIdNumber = Number(projectId);
  const defaultBoards = defaultBoardsQuery.data?.defaultBoards ?? [];
  const projectPreference = defaultBoards.find(
    (preference) => Number(preference.projectId) === projectIdNumber,
  );
  const cleanupKey = projectPreference
    ? `${location.key}:${userId}:${projectIdNumber}:${projectPreference.boardId}`
    : null;
  const activeCleanupAttempt = cleanupAttempt?.key === cleanupKey
    ? cleanupAttempt
    : null;
  const cleanupError = activeCleanupAttempt?.status === 'error'
    ? activeCleanupAttempt.error
    : null;
  const decision = resolveProjectEntry(
    boardsQuery.data ?? [],
    projectId,
    defaultBoards,
    cleanupError,
  );
  const isPending =
    projectQuery.isPending ||
    boardsQuery.isPending ||
    userQuery.isPending ||
    (Boolean(userId) && defaultBoardsQuery.isPending);
  const error =
    projectQuery.error ??
    boardsQuery.error ??
    userQuery.error ??
    (userId ? defaultBoardsQuery.error : null);
  const isError = Boolean(
    projectQuery.isError ||
    boardsQuery.isError ||
    userQuery.isError ||
    (userId && defaultBoardsQuery.isError),
  );
  const workspaceReady = !isPending && !isError && Boolean(userId);
  const isCleaningPreference = decision.needsCleanup && (
    !activeCleanupAttempt || activeCleanupAttempt.status === 'pending'
  );

  useEffect(() => {
    if (!decision.needsCleanup || !cleanupKey) return;
    if (cleanupAttemptRef.current?.key === cleanupKey) return;

    const attempt = { key: cleanupKey, status: 'pending' };
    cleanupAttemptRef.current = attempt;
    setCleanupAttempt(attempt);

    clearPreference(projectIdNumber).then(
      () => {
        const completedAttempt = { key: cleanupKey, status: 'cleared' };
        cleanupAttemptRef.current = completedAttempt;
        setCleanupAttempt(completedAttempt);
      },
      (mutationError) => {
        const failedAttempt = {
          key: cleanupKey,
          status: 'error',
          error: mutationError,
        };
        cleanupAttemptRef.current = failedAttempt;
        setCleanupAttempt(failedAttempt);
      },
    );
  }, [
    cleanupKey,
    clearPreference,
    decision.needsCleanup,
    projectIdNumber,
    cleanupRetry,
  ]);

  useEffect(() => {
    if (!workspaceReady || isCleaningPreference || !decision.board) return;

    navigate(`/projects/${projectIdNumber}/boards/${decision.board.id}`, {
      replace: true,
      state: {
        defaultBoardId: decision.isDefault ? decision.board.id : null,
        defaultPreferenceError: decision.cleanupError
          ? getPreferenceErrorMessage(decision.cleanupError)
          : null,
      },
    });
  }, [
    decision.board,
    decision.cleanupError,
    decision.isDefault,
    isCleaningPreference,
    navigate,
    projectIdNumber,
    workspaceReady,
  ]);

  async function handleCreateBoard(payload) {
    const board = await createBoardMutation.mutateAsync(payload);
    setShowCreateDialog(false);
    navigate(`/projects/${projectId}/boards/${board.id}`, {
      state: { defaultBoardId: null },
    });
  }

  function closeCreateDialog() {
    setShowCreateDialog(false);
    createBoardMutation.reset();
  }

  function retryQueries() {
    setCleanupAttempt(null);
    cleanupAttemptRef.current = null;
    resetClearPreference();
    setCleanupRetry((attempt) => attempt + 1);
    projectQuery.refetch();
    boardsQuery.refetch();
    userQuery.refetch();
    if (userId) defaultBoardsQuery.refetch();
  }

  if (isPending || isCleaningPreference || (workspaceReady && decision.board)) {
    return (
      <div className="page-state" aria-live="polite">
        {isCleaningPreference ? 'Clearing an outdated Board preference…' : 'Opening Project workspace…'}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="empty-state" role="alert">
        <strong>{getErrorTitle(error)}</strong>
        <span>{getErrorMessage(error)}</span>
        <button className="secondary-button" onClick={retryQueries} type="button">
          Try again
        </button>
        <Link className="board-text-link" to="/projects">Back to All Projects</Link>
      </div>
    );
  }

  const project = projectQuery.data;
  const isOwner = project.role === 'OWNER';

  return (
    <>
      <section className="project-entry-page" aria-label={`${project.name} Boards`}>
        <Link className="board-back-link" to="/projects">← All Projects</Link>
        <div className="empty-state board-empty-state">
          <span className="board-empty-icon" aria-hidden="true">▤</span>
          <strong>No Boards yet</strong>
          <span>
            {isOwner
              ? 'Create the first Board for this Project.'
              : 'The Project Owner has not created a Board yet.'}
          </span>
          {decision.cleanupError && (
            <p className="form-inline-error" role="alert">
              The saved default could not be cleared. {getPreferenceErrorMessage(decision.cleanupError)}
            </p>
          )}
          {isOwner && (
            <button
              className="primary-button"
              disabled={createBoardMutation.isPending}
              onClick={() => {
                createBoardMutation.reset();
                setShowCreateDialog(true);
              }}
              type="button"
            >
              <span aria-hidden="true">+</span> New Board
            </button>
          )}
        </div>
      </section>
      <BoardCreateDialog
        error={createBoardMutation.isError ? getErrorMessage(createBoardMutation.error) : null}
        isCreating={createBoardMutation.isPending}
        onClose={closeCreateDialog}
        onCreate={handleCreateBoard}
        open={showCreateDialog}
      />
    </>
  );
}
