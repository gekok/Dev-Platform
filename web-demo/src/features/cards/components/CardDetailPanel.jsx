import { useEffect, useRef, useState } from 'react';
import {
  useAddComment,
  useAssignCard,
  useCard,
  useCardComments,
} from '../hooks/useCards.js';

function getErrorMessage(error, fallback) {
  return error.response?.data?.message ?? fallback;
}

function formatDateTime(value) {
  if (!value) return 'No due date';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'No due date';
  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  });
}

function formatCommentDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: 'UTC',
  });
}

export default function CardDetailPanel({ cardId, boardId, members, onClose }) {
  const cardQuery = useCard(cardId);
  const commentsQuery = useCardComments(cardId);
  const assignMutation = useAssignCard(boardId);
  const addCommentMutation = useAddComment(boardId, cardId);
  const closeButtonRef = useRef(null);
  const [comment, setComment] = useState('');
  const [mentionedUserIds, setMentionedUserIds] = useState([]);
  const membersById = new Map(members.map((member) => [member.id, member]));
  const card = cardQuery.data;

  useEffect(() => {
    const previouslyFocusedElement = document.activeElement;
    closeButtonRef.current?.focus();
    function handleKeyDown(event) {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab') return;

      const panel = closeButtonRef.current?.closest('[role="dialog"]');
      const focusableElements = panel?.querySelectorAll(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      const focusable = [...(focusableElements ?? [])];
      if (focusable.length === 0) {
        event.preventDefault();
        closeButtonRef.current?.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const activeElement = document.activeElement;
      if (event.shiftKey && (activeElement === first || !panel?.contains(activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (activeElement === last || !panel?.contains(activeElement))) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      if (previouslyFocusedElement instanceof HTMLElement && previouslyFocusedElement.isConnected) {
        previouslyFocusedElement.focus();
      }
    };
  }, [onClose]);

  async function handleCommentSubmit(event) {
    event.preventDefault();
    const content = comment.trim();
    if (!content) return;
    try {
      await addCommentMutation.mutateAsync({ content, mentionedUserIds });
      setComment('');
      setMentionedUserIds([]);
    } catch {
      // The mutation error is rendered beside the comment form.
    }
  }

  function toggleMention(memberId) {
    setMentionedUserIds((current) =>
      current.includes(memberId)
        ? current.filter((id) => id !== memberId)
        : [...current, memberId],
    );
  }

  return (
    <>
      <button
        aria-label="Close Card details"
        className="card-detail-backdrop"
        onClick={onClose}
        type="button"
      />
      <aside
        aria-labelledby="card-detail-title"
        aria-modal="true"
        className="card-detail-panel"
        role="dialog"
      >
        <header className="card-detail-header">
          <div>
            <span className="eyebrow">CARD DETAILS</span>
            <h2 id="card-detail-title">{card?.title ?? 'Card details'}</h2>
          </div>
          <button
            aria-label="Close Card details"
            className="card-detail-close"
            onClick={onClose}
            ref={closeButtonRef}
            type="button"
          >
            ×
          </button>
        </header>

        {cardQuery.isPending && <p className="page-state" aria-live="polite">Loading Card…</p>}
        {cardQuery.isError && (
          <div className="empty-state" role="alert">
            <span>{getErrorMessage(cardQuery.error, 'The Card could not be loaded.')}</span>
            <button className="secondary-button" onClick={() => cardQuery.refetch()} type="button">
              Try again
            </button>
          </div>
        )}

        {card && (
          <div className="card-detail-content">
            <section className="card-detail-section">
              <h3>Description</h3>
              <p className="card-detail-description">
                {card.description || 'No description has been added.'}
              </p>
              <div className="card-detail-due-date">
                <span>Due date</span>
                <strong>{formatDateTime(card.dueDate)}</strong>
              </div>
            </section>

            <section className="card-detail-section">
              <label htmlFor="card-assignee-select">Assignee</label>
              <select
                id="card-assignee-select"
                disabled={assignMutation.isPending}
                onChange={(event) => {
                  assignMutation.mutate({ cardId, assigneeId: event.target.value || null });
                }}
                value={card.assigneeId ?? ''}
              >
                <option value="">Unassigned</option>
                {members.map((member) => (
                  <option key={member.id} value={member.id}>{member.fullName}</option>
                ))}
              </select>
              {assignMutation.isError && (
                <p className="form-inline-error" role="alert">
                  {getErrorMessage(assignMutation.error, 'The assignee could not be updated.')}
                </p>
              )}
            </section>

            <section aria-labelledby="card-comments-title" className="card-detail-section card-comments-section">
              <div className="card-comments-heading">
                <h3 id="card-comments-title">Comments</h3>
                <span>{commentsQuery.data?.length ?? 0}</span>
              </div>
              {commentsQuery.isPending && <p className="card-comments-state">Loading comments…</p>}
              {commentsQuery.isError && (
                <div className="card-comments-state" role="alert">
                  <span>{getErrorMessage(commentsQuery.error, 'Comments could not be loaded.')}</span>
                  <button className="board-text-link" onClick={() => commentsQuery.refetch()} type="button">
                    Try again
                  </button>
                </div>
              )}
              {commentsQuery.isSuccess && commentsQuery.data.length === 0 && (
                <p className="card-comments-state">No comments yet. Start the conversation.</p>
              )}
              {commentsQuery.data?.length > 0 && (
                <ol className="card-comment-list">
                  {commentsQuery.data.map((item) => (
                    <li className="card-comment" key={item.id}>
                      <div className="card-comment-meta">
                        <strong>{membersById.get(item.authorId)?.fullName ?? 'Project member'}</strong>
                        <time dateTime={item.createdAt}>{formatCommentDate(item.createdAt)}</time>
                      </div>
                      <p>{item.content}</p>
                    </li>
                  ))}
                </ol>
              )}

              <form className="card-comment-form" onSubmit={handleCommentSubmit}>
                <label htmlFor="card-comment-input">Add a comment</label>
                <textarea
                  id="card-comment-input"
                  onChange={(event) => setComment(event.target.value)}
                  placeholder="Share an update…"
                  required
                  rows={3}
                  value={comment}
                />
                <fieldset className="card-mention-options">
                  <legend>Mention Project members</legend>
                  {members.map((member) => (
                    <label key={member.id}>
                      <input
                        checked={mentionedUserIds.includes(member.id)}
                        onChange={() => toggleMention(member.id)}
                        type="checkbox"
                      />
                      <span>{member.fullName}</span>
                    </label>
                  ))}
                </fieldset>
                {addCommentMutation.isError && (
                  <p className="form-inline-error" role="alert">
                    {getErrorMessage(addCommentMutation.error, 'The comment could not be added.')}
                  </p>
                )}
                <button
                  className="primary-button"
                  disabled={addCommentMutation.isPending || !comment.trim()}
                  type="submit"
                >
                  {addCommentMutation.isPending ? 'Posting…' : 'Post comment'}
                </button>
              </form>
            </section>
          </div>
        )}
      </aside>
    </>
  );
}
