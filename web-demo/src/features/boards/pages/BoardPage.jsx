import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useProject, useProjectMembers } from '../../projects/hooks/useProjects.js';
import { useCards, useCreateCard, useMoveCard } from '../../cards/hooks/useCards.js';
import CardDetailPanel from '../../cards/components/CardDetailPanel.jsx';
import {
  useAddColumn,
  useBoard,
  useReorderColumns,
} from '../hooks/useBoards.js';
import BoardHeader from '../components/BoardHeader.jsx';
import ColumnCreateForm from '../components/ColumnCreateForm.jsx';
import KanbanColumns from '../components/KanbanColumns.jsx';
import '../../cards/cards.css';
import '../boards.css';

function getErrorTitle(error) {
  switch (error.response?.status) {
    case 401:
      return 'Your demo session has expired.';
    case 403:
      return 'Board access denied.';
    case 404:
      return 'Board not found.';
    default:
      return 'We couldn’t open this Board.';
  }
}

function getErrorMessage(error) {
  switch (error.response?.status) {
    case 401:
      return 'Sign in again to continue.';
    case 403:
      return 'You are not a member of the Project that owns this Board.';
    case 404:
      return 'Check the Board link and try again.';
    default:
      return error.response?.data?.message ?? 'The Board workspace could not be loaded. Try again.';
  }
}

export default function BoardPage() {
  const { projectId, boardId } = useParams();
  const location = useLocation();
  const [showColumnForm, setShowColumnForm] = useState(false);
  const [selectedCardContext, setSelectedCardContext] = useState(null);
  const projectQuery = useProject(projectId);
  const boardQuery = useBoard(boardId);
  const cardsQuery = useCards(boardId);
  const membersQuery = useProjectMembers(projectId);
  const addColumnMutation = useAddColumn(projectId, boardId);
  const reorderMutation = useReorderColumns(projectId, boardId);
  const createCardMutation = useCreateCard(boardId);
  const moveCardMutation = useMoveCard(boardId);
  const isOwner = projectQuery.data?.role === 'OWNER';
  const selectedCardId =
    selectedCardContext?.projectId === projectId && selectedCardContext?.boardId === boardId
      ? selectedCardContext.cardId
      : null;

  useEffect(() => {
    setSelectedCardContext(null);
    setShowColumnForm(false);
  }, [projectId, boardId]);

  const closeCardDetails = useCallback(() => setSelectedCardContext(null), []);

  async function handleAddColumn(name) {
    await addColumnMutation.mutateAsync({ name });
    setShowColumnForm(false);
  }

  function handleMoveColumn(columnId, direction) {
    const columns = [...(boardQuery.data?.columns ?? [])];
    const currentIndex = columns.findIndex((column) => column.id === columnId);
    const targetIndex = currentIndex + direction;
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= columns.length) return;

    [columns[currentIndex], columns[targetIndex]] = [
      columns[targetIndex],
      columns[currentIndex],
    ];
    reorderMutation.mutate(columns.map((column) => column.id));
  }

  async function handleCreateCard(payload) {
    await createCardMutation.mutateAsync(payload);
  }

  function handleMoveCard(cardId, toColumnId, requestedPosition = null) {
    if (moveCardMutation.isPending) return;
    const cards = cardsQuery.data ?? [];
    const card = cards.find((item) => item.id === cardId);
    const destinationColumn = boardQuery.data?.columns.some((item) => item.id === toColumnId);
    if (!card || !destinationColumn) return;

    const destinationCards = cards
      .filter((item) => item.columnId === toColumnId && item.id !== cardId)
      .sort((left, right) => left.position - right.position);
    const toPosition = requestedPosition == null
      ? destinationCards.length
      : Math.min(Math.max(requestedPosition, 0), destinationCards.length);
    if (card.columnId === toColumnId && card.position === toPosition) return;

    moveCardMutation.mutate({ cardId, toColumnId, toPosition });
  }

  function handleDropCard(draggedCardId, toColumnId, beforeCardId) {
    if (draggedCardId === beforeCardId) return;
    const destinationCards = (cardsQuery.data ?? [])
      .filter((item) => item.columnId === toColumnId && item.id !== draggedCardId)
      .sort((left, right) => left.position - right.position);
    const requestedPosition = beforeCardId == null
      ? destinationCards.length
      : destinationCards.findIndex((item) => item.id === beforeCardId);
    if (requestedPosition < 0) return;
    handleMoveCard(draggedCardId, toColumnId, requestedPosition);
  }

  function handleOpenCard(cardId) {
    setSelectedCardContext({ projectId, boardId, cardId });
  }

  if (
    projectQuery.isPending ||
    boardQuery.isPending ||
    cardsQuery.isPending ||
    membersQuery.isPending
  ) {
    return <div className="page-state" aria-live="polite">Loading Board…</div>;
  }

  if (projectQuery.isError || boardQuery.isError || cardsQuery.isError || membersQuery.isError) {
    const error = projectQuery.error ?? boardQuery.error ?? cardsQuery.error ?? membersQuery.error;
    return (
      <div className="empty-state" role="alert">
        <strong>{getErrorTitle(error)}</strong>
        <span>{getErrorMessage(error)}</span>
        <button
          className="secondary-button"
          onClick={() => {
            projectQuery.refetch();
            boardQuery.refetch();
            cardsQuery.refetch();
            membersQuery.refetch();
          }}
          type="button"
        >
          Try again
        </button>
        <Link className="board-text-link" to="/projects">Back to All Projects</Link>
      </div>
    );
  }

  const project = projectQuery.data;
  const board = boardQuery.data;
  const isDefault = Number(location.state?.defaultBoardId) === Number(boardId);
  const defaultPreferenceError = location.state?.defaultPreferenceError;
  if (board.projectId !== Number(projectId)) {
    return (
      <div className="empty-state" role="alert">
        <strong>This Board belongs to another Project.</strong>
        <span>Open the Board from its own Project workspace.</span>
        <Link className="board-text-link" to="/projects">Back to All Projects</Link>
      </div>
    );
  }

  return (
    <>
    <section className="board-page" aria-labelledby="board-title">
      <BoardHeader board={board} isDefault={isDefault} project={project} />

      {defaultPreferenceError && (
        <p className="form-inline-error board-preference-error" role="alert">
          {defaultPreferenceError}
        </p>
      )}

      <div className="board-toolbar">
        <div>
          <h2>Board Columns</h2>
          <span>{board.columns.length} stages for this team’s work</span>
        </div>
        {isOwner && (
          <button
            aria-controls="column-create-form"
            aria-expanded={showColumnForm}
            className="secondary-button"
            disabled={addColumnMutation.isPending || reorderMutation.isPending}
            onClick={() => {
              addColumnMutation.reset();
              setShowColumnForm((visible) => !visible);
            }}
            type="button"
          >
            <span aria-hidden="true">+</span> Add Column
          </button>
        )}
      </div>

      {showColumnForm && isOwner && (
        <ColumnCreateForm
          error={addColumnMutation.isError ? getErrorMessage(addColumnMutation.error) : null}
          isCreating={addColumnMutation.isPending}
          onCancel={() => {
            setShowColumnForm(false);
            addColumnMutation.reset();
          }}
          onCreate={handleAddColumn}
        />
      )}

      {reorderMutation.isError && (
        <p className="form-inline-error board-mutation-error" role="alert">
          {getErrorMessage(reorderMutation.error)}
        </p>
      )}

      {moveCardMutation.isError && (
        <p className="form-inline-error board-mutation-error" role="alert">
          {getErrorMessage(moveCardMutation.error)}
        </p>
      )}

      <KanbanColumns
        columns={board.columns}
        cards={cardsQuery.data}
        members={membersQuery.data}
        isCreatingCard={createCardMutation.isPending}
        cardCreateError={createCardMutation.isError ? getErrorMessage(createCardMutation.error) : null}
        isMovingCard={moveCardMutation.isPending}
        isOwner={isOwner}
        isReordering={reorderMutation.isPending}
        onResetCardCreate={() => createCardMutation.reset()}
        onCreateCard={handleCreateCard}
        onOpenCard={handleOpenCard}
        onMoveCard={handleMoveCard}
        onDropCard={handleDropCard}
        onMoveColumn={handleMoveColumn}
      />
    </section>
    {selectedCardId !== null && (
        <CardDetailPanel
          boardId={boardId}
          cardId={selectedCardId}
          key={`${projectId}:${boardId}:${selectedCardId}`}
          members={membersQuery.data}
          onClose={closeCardDetails}
        />
      )}
    </>
  );
}
