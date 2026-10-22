export default function CardMoveControl({
  card,
  columns,
  isMoving,
  onMoveToColumn,
}) {
  return (
    <label className="card-move-control">
      <span>Move to</span>
      <select
        aria-label={`Move ${card.title} to Column`}
        disabled={isMoving}
        onChange={(event) => onMoveToColumn(card.id, Number(event.target.value))}
        value={card.columnId}
      >
        {columns.map((column) => (
          <option key={column.id} value={column.id}>{column.name}</option>
        ))}
      </select>
    </label>
  );
}
