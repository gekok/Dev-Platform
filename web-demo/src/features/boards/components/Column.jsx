import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import CardCreateForm from '../../cards/components/CardCreateForm.jsx';
import CardItem from '../../cards/components/CardItem.jsx';
import { getApiErrorMessage, getApiErrorStatus } from '../../../shared/services/apiErrors.js';
import { queryKeys } from '../../../shared/query/queryKeys.js';
import { useCards } from '../../cards/hooks/useCards.js';
import { flattenCardPages } from '../../cards/cardPageUtils.js';

export default function Column({
  projectId,
  boardId,
  column,
  columns,
  index,
  members,
  isCreatingCard,
  cardCreateError,
  isMovingCard,
  isOwner,
  isReordering,
  onResetCardCreate,
  onCreateCard,
  onOpenCard,
  onMoveCard,
  onDropCard,
  onMoveColumn,
  onCardsChange,
  onRegisterColumnLoader,
  totalColumns,
}) {
  const [showCardForm, setShowCardForm] = useState(false);
  const queryClient = useQueryClient();
  const cardsQuery = useCards(projectId, boardId, column.id);
  const cardsQueryRef = useRef(cardsQuery);
  const bodyRef = useRef(null);
  const loadMoreSentinelRef = useRef(null);
  const loaderRef = useRef(null);
  cardsQueryRef.current = cardsQuery;
  const orderedCards = cardsQuery.cards;
  const membersById = new Map(members.map((member) => [member.id, member]));
  const staleCursorError = cardsQuery.isFetchNextPageError &&
    getApiErrorStatus(cardsQuery.error) === 400;
  const {
    fetchNextPage,
    hasDuplicateCards,
    hasNextPage,
    isFetchNextPageError,
    isFetchingNextPage,
  } = cardsQuery;
  const cardQueryKey = queryKeys.cards(projectId, boardId, column.id);

  useEffect(() => {
    onCardsChange(column.id, orderedCards);
  }, [column.id, onCardsChange, orderedCards]);

  loaderRef.current = {
    loadAllCards: async () => {
      let result = cardsQueryRef.current;
      let pageData = result.data;
      while (result.hasNextPage) {
        result = await result.fetchNextPage({ cancelRefetch: false });
        if (result.isFetchNextPageError) throw result.error;
        pageData = result.data;
      }
      return flattenCardPages(pageData?.pages ?? []);
    },
  };

  useEffect(() => {
    onRegisterColumnLoader(column.id, loaderRef);
    return () => onRegisterColumnLoader(column.id, null);
  }, [column.id, onRegisterColumnLoader]);

  useEffect(() => {
    if (
      !hasNextPage ||
      isFetchingNextPage ||
      isFetchNextPageError ||
      hasDuplicateCards ||
      typeof IntersectionObserver === 'undefined' ||
      !loadMoreSentinelRef.current
    ) return undefined;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        fetchNextPage({ cancelRefetch: false }).catch(() => {});
      }
    }, {
      root: bodyRef.current,
      rootMargin: '120px 0px',
    });
    observer.observe(loadMoreSentinelRef.current);
    return () => observer.disconnect();
  }, [
    fetchNextPage,
    hasDuplicateCards,
    hasNextPage,
    isFetchNextPageError,
    isFetchingNextPage,
  ]);

  function reloadCards() {
    return queryClient.resetQueries({ queryKey: cardQueryKey, exact: true });
  }

  function loadMoreCards() {
    return cardsQuery.fetchNextPage({ cancelRefetch: false });
  }

  function handleDrop(event, beforeCardId = null) {
    event.preventDefault();
    if (isMovingCard) return;
    const draggedCardId = Number(event.dataTransfer.getData('text/plain'));
    if (Number.isSafeInteger(draggedCardId) && draggedCardId > 0) {
      onDropCard(draggedCardId, column.id, beforeCardId);
    }
  }

  return (
    <section className="kanban-column" aria-labelledby={`column-title-${column.id}`}>
      <header className="kanban-column-header">
        <div className="kanban-column-title-wrap">
          <span className="column-position-mark" aria-hidden="true">{index + 1}</span>
          <div>
            <h3 id={`column-title-${column.id}`}>{column.name}</h3>
            <span className="column-card-count">
              {orderedCards.length}{cardsQuery.hasNextPage ? '+' : ''} {orderedCards.length === 1 && !cardsQuery.hasNextPage ? 'card' : 'cards'}
            </span>
          </div>
        </div>
        <div className="column-header-actions">
          <button
            aria-controls={`card-create-form-${column.id}`}
            aria-expanded={showCardForm}
            className="column-add-card"
            disabled={isCreatingCard || isMovingCard}
            onClick={() => {
              if (!showCardForm) onResetCardCreate();
              setShowCardForm((visible) => !visible);
            }}
            type="button"
          >
            + Card
          </button>
          {isOwner && (
            <div className="column-reorder-controls" role="group" aria-label={`Reorder ${column.name}`}>
              <button
                aria-label={`Move ${column.name} left`}
                disabled={index === 0 || isReordering}
                onClick={() => onMoveColumn(column.id, -1)}
                type="button"
              >
                ←
              </button>
              <button
                aria-label={`Move ${column.name} right`}
                disabled={index === totalColumns - 1 || isReordering}
                onClick={() => onMoveColumn(column.id, 1)}
                type="button"
              >
                →
              </button>
            </div>
          )}
        </div>
      </header>
      {showCardForm && (
        <CardCreateForm
          columnId={column.id}
          error={cardCreateError}
          isCreating={isCreatingCard}
          members={members}
          onCancel={() => {
            setShowCardForm(false);
            onResetCardCreate();
          }}
          onCreate={async (payload) => {
            await onCreateCard(payload);
            setShowCardForm(false);
          }}
        />
      )}
      <div
        className="kanban-column-body"
        ref={bodyRef}
        onDragOver={(event) => {
          if (!isMovingCard) {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
          }
        }}
        onDrop={(event) => handleDrop(event)}
      >
        {cardsQuery.isPending ? (
          <p className="column-card-state" aria-live="polite">Loading cards...</p>
        ) : cardsQuery.isError && !cardsQuery.data ? (
          <div className="column-card-state" role="alert">
            <span>{getApiErrorMessage(cardsQuery.error, 'Cards could not be loaded.')}</span>
            <button className="secondary-button" onClick={() => cardsQuery.refetch()} type="button">
              Try again
            </button>
          </div>
        ) : orderedCards.length === 0 ? (
          <div className="column-empty-hint">
            <span aria-hidden="true">＋</span>
            <span>No cards yet. Add one to get started.</span>
          </div>
        ) : orderedCards.map((card) => (
          <CardItem
            card={card}
            columns={columns}
            isMoving={isMovingCard}
            key={card.id}
            membersById={membersById}
            onDropBefore={(draggedCardId, beforeCardId) => {
              onDropCard(draggedCardId, column.id, beforeCardId);
            }}
            onMoveToColumn={onMoveCard}
            onOpen={onOpenCard}
          />
        ))}
        {cardsQuery.hasDuplicateCards && (
          <div className="column-card-state" role="status">
            <span>Cards changed while loading. Reload this Column to refresh the list.</span>
            <button className="secondary-button" onClick={reloadCards} type="button">
              Reload Column
            </button>
          </div>
        )}
        {cardsQuery.isFetchNextPageError && !cardsQuery.hasDuplicateCards && (
          <div className="column-card-state" role="alert">
            <span>{staleCursorError
              ? 'This page cursor is no longer valid. Reload this Column to continue.'
              : getApiErrorMessage(cardsQuery.error, 'More cards could not be loaded.')}</span>
            {staleCursorError ? (
              <button className="secondary-button" onClick={reloadCards} type="button">
                Reload Column
              </button>
            ) : (
              <button className="secondary-button" onClick={loadMoreCards} type="button">
                Retry
              </button>
            )}
          </div>
        )}
        {cardsQuery.hasNextPage && !cardsQuery.isFetchNextPageError && !cardsQuery.hasDuplicateCards && (
          <div className="column-load-more" ref={loadMoreSentinelRef}>
            <button
              className="secondary-button"
              disabled={cardsQuery.isFetchingNextPage}
              onClick={loadMoreCards}
              type="button"
            >
              {cardsQuery.isFetchingNextPage ? 'Loading...' : 'Load more cards'}
            </button>
          </div>
        )}
        {cardsQuery.isFetchingNextPage && !cardsQuery.isFetchNextPageError && (
          <p className="column-card-state" aria-live="polite">Loading more cards...</p>
        )}
      </div>
    </section>
  );
}
