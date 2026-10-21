import { Link } from 'react-router-dom';
import BoardSelector from './BoardSelector.jsx';

export default function BoardHeader({ board, project, isDefault = false, selectorProps }) {
  return (
    <header className="board-page-header">
      <Link className="board-back-link" to="/projects">
        ← All Projects
      </Link>
      <div className="board-title-row">
        <div>
          <span className="eyebrow">KANBAN BOARD</span>
          <BoardSelector {...selectorProps} currentBoard={board} />
        </div>
        <div className="board-header-badges">
          {isDefault && <span className="board-default-badge">Default</span>}
          <span className="board-key-badge">{board.key}</span>
          <span className={`role-chip role-${project.role.toLowerCase()}`}>
            {project.role === 'OWNER' ? 'Owner' : 'Member'}
          </span>
        </div>
      </div>
    </header>
  );
}
