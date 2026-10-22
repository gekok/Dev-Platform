import CardMoveControl from './CardMoveControl.jsx';

function initials(name) {
  return name
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

export default function CardItem({
  card,
  columns,
  membersById,
  isMoving,
  onOpen,
  onMoveToColumn,
  onDropBefore,
}) {
  const assignee = membersById.get(card.assigneeId);

  return (
    <article
      className="kanban-card"
      draggable={!isMoving}
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', String(card.id));
      }}
      onDrop={(event) => {
        event.preventDefault();
        event.stopPropagation();
        onDropBefore(Number(event.dataTransfer.getData('text/plain')), card.id);
      }}
    >
      <button className="kanban-card-title" onClick={() => onOpen(card.id)} type="button">
        {card.title}
      </button>
      <div className="kanban-card-meta">
        <span className="card-assignee" title={assignee?.fullName ?? 'Unassigned'}>
          {assignee ? (
            <span aria-hidden="true" className="card-assignee-avatar">{initials(assignee.fullName)}</span>
          ) : <span aria-hidden="true" className="card-assignee-avatar card-unassigned">—</span>}
          <span>{assignee?.fullName ?? 'Unassigned'}</span>
        </span>
      </div>
      <CardMoveControl
        card={card}
        columns={columns}
        isMoving={isMoving}
        onMoveToColumn={onMoveToColumn}
      />
    </article>
  );
}
