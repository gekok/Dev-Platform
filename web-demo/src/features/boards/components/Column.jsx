import { useState } from 'react';
import CardCreateForm from '../../cards/components/CardCreateForm.jsx';
import CardItem from '../../cards/components/CardItem.jsx';

export default function Column({
  column,
  columns,
  index,
  cards,
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
  totalColumns,
}) {
  const [showCardForm, setShowCardForm] = useState(false);
  const orderedCards = cards
    .filter((card) => card.columnId === column.id)
    .sort((left, right) => left.position - right.position);
  const membersById = new Map(members.map((member) => [member.id, member]));

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
            <span className="column-card-count">{orderedCards.length} {orderedCards.length === 1 ? 'card' : 'cards'}</span>
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
        onDragOver={(event) => {
          if (!isMovingCard) {
            event.preventDefault();
            event.dataTransfer.dropEffect = 'move';
          }
        }}
        onDrop={(event) => handleDrop(event)}
      >
        {orderedCards.length === 0 ? (
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
      </div>
    </section>
  );
}
