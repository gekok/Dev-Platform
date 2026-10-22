import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <section aria-labelledby="not-found-title" className="empty-state route-not-found">
      <strong id="not-found-title">Page not found</strong>
      <span>This link does not point to a Project, Board, or demo page.</span>
      <Link className="board-text-link" to="/projects">Back to Projects</Link>
    </section>
  );
}
