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
import {
  getCleanupAttemptForRoute,
  isCurrentCleanupAttempt,
  releaseCleanupAttempt,
  resolveProjectEntry,
} from '../projectEntry.js';
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
  const cleanupExpectedBoardId = projectPreference
    ? Number(projectPreference.boardId)
    : null;
  const cleanupKey = projectPreference
    ? `${location.key}:${userId}:${projectIdNumber}:${projectPreference.boardId}`
    : null;
  const cleanupKeyRef = useRef(cleanupKey);
  cleanupKeyRef.current = cleanupKey;
  const isMountedRef = useRef(false);
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
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!decision.needsCleanup || !cleanupKey) return;

    function releaseAttemptOnRouteChange(requestAttempt) {
      cleanupAttemptRef.current = releaseCleanupAttempt({
        currentKey: cleanupKeyRef.current,
        currentAttempt: cleanupAttemptRef.current,
        requestKey: cleanupKey,
        requestAttempt,
      });
    }

    const existingAttempt = getCleanupAttemptForRoute(
      cleanupAttemptRef.current,
      cleanupKey,
    );
    if (existingAttempt) {
      return () => releaseAttemptOnRouteChange(existingAttempt);
    }

    const attempt = { key: cleanupKey, status: 'pending' };
    cleanupAttemptRef.current = attempt;
    setCleanupAttempt(attempt);

    function updateCleanupAttempt(status, error = null) {
      if (!isCurrentCleanupAttempt({
        isMounted: isMountedRef.current,
        currentKey: cleanupKeyRef.current,
        currentAttempt: cleanupAttemptRef.current,
        requestKey: cleanupKey,
        requestAttempt: attempt,
      })) {
        return;
      }

      const result = {
        key: cleanupKey,
        status,
        ...(status === 'error' ? { error } : {}),
      };
      cleanupAttemptRef.current = result;
      setCleanupAttempt(result);
    }

    clearPreference({
      projectId: projectIdNumber,
      expectedBoardId: cleanupExpectedBoardId,
    }).then(
      () => updateCleanupAttempt('cleared'),
      (mutationError) => updateCleanupAttempt('error', mutationError),
    );

    return () => {
      releaseAttemptOnRouteChange(attempt);
    };
  }, [
    cleanupKey,
    cleanupExpectedBoardId,
    clearPreference,
    decision.needsCleanup,
    projectIdNumber,
    cleanupRetry,
  ]);

  useEffect(() => {
    if (!workspaceReady || isCleaningPreference || !decision.board) return;

    navigate(`/projects/${projectIdNumber}/boards/${decision.board.id}`, {
      replace: true,
      state: decision.cleanupError
        ? { defaultPreferenceError: getPreferenceErrorMessage(decision.cleanupError) }
        : null,
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
    navigate(`/projects/${projectId}/boards/${board.id}`);
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
            <>
              <p className="form-inline-error" role="alert">
                The saved default could not be cleared. {getPreferenceErrorMessage(decision.cleanupError)}
              </p>
              <button className="secondary-button" onClick={retryQueries} type="button">
                Try again
              </button>
            </>
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
