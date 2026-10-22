import Column from './Column.jsx';

export default function KanbanColumns({
  columns,
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
}) {
  const orderedColumns = [...columns].sort((left, right) => left.position - right.position);

  return (
    <div
      aria-label="Kanban columns"
      className="kanban-board-scroll"
      role="region"
      tabIndex={0}
    >
      <div className="kanban-columns">
        {orderedColumns.map((column, index) => (
          <Column
            cards={cards}
            cardCreateError={cardCreateError}
            column={column}
            columns={orderedColumns}
            index={index}
            isCreatingCard={isCreatingCard}
            isMovingCard={isMovingCard}
            isOwner={isOwner}
            isReordering={isReordering}
            key={column.id}
            members={members}
            onCreateCard={onCreateCard}
            onDropCard={onDropCard}
            onMoveCard={onMoveCard}
            onMoveColumn={onMoveColumn}
            onOpenCard={onOpenCard}
            onResetCardCreate={onResetCardCreate}
            totalColumns={orderedColumns.length}
          />
        ))}
      </div>
    </div>
  );
}
