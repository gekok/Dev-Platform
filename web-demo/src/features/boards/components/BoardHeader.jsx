import BoardSelector from './BoardSelector.jsx';

export default function BoardHeader({ board, project, isDefault = false, selectorProps }) {
  return (
    <div aria-label="Board selection and details" className="board-header-context" role="group">
      <BoardSelector {...selectorProps} currentBoard={board} />
      <div className="board-header-badges">
        {isDefault && <span className="board-default-badge">Default</span>}
        <span className="board-key-badge">{board.key}</span>
        <span className={`role-chip role-${project.role.toLowerCase()}`}>
          {project.role === 'OWNER' ? 'Owner' : 'Member'}
        </span>
      </div>
    </div>
  );
}
