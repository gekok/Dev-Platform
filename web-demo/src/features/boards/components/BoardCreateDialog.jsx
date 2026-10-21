import { useEffect, useRef, useState } from 'react';

export default function BoardCreateDialog({
  open,
  onClose,
  onCreate,
  isCreating,
  error,
}) {
  const dialogRef = useRef(null);
  const returnFocusRef = useRef(null);
  const [name, setName] = useState('');
  const [key, setKey] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!open || !dialog) return undefined;

    returnFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    dialog.showModal();
    dialog.querySelector('input')?.focus();

    return () => {
      if (dialog.open) dialog.close();
      if (returnFocusRef.current?.isConnected) returnFocusRef.current.focus();
      returnFocusRef.current = null;
    };
  }, [open]);

  async function handleSubmit(event) {
    event.preventDefault();
    try {
      await onCreate({ name: name.trim(), key: key.trim() });
    } catch {
      // The caller keeps the dialog open and renders the mutation error.
    }
  }

  function handleCancel(event) {
    event.preventDefault();
    if (!isCreating) onClose();
  }

  return (
    <dialog
      aria-describedby="board-create-note"
      aria-labelledby="board-create-title"
      className="board-create-dialog"
      onCancel={handleCancel}
      ref={dialogRef}
    >
      <div className="board-create-dialog-content">
        <header className="board-create-dialog-heading">
          <div>
            <span className="eyebrow">PROJECT BOARD</span>
            <h2 id="board-create-title">Create a Board</h2>
          </div>
          <button
            aria-label="Close dialog"
            className="icon-button board-dialog-close"
            disabled={isCreating}
            onClick={onClose}
            type="button"
          >
            <span aria-hidden="true">×</span>
          </button>
        </header>

        <p className="board-form-note" id="board-create-note">
          New Boards start with To Do, In Progress, and Done Columns.
        </p>

        <form className="board-create-form board-create-dialog-form" onSubmit={handleSubmit}>
          <div className="board-create-fields">
            <div>
              <label htmlFor="board-name">Board name</label>
              <input
                autoComplete="off"
                autoFocus
                id="board-name"
                maxLength={100}
                onChange={(event) => setName(event.target.value)}
                placeholder="e.g. Product roadmap"
                required
                value={name}
              />
            </div>
            <div>
              <label htmlFor="board-key">Board key</label>
              <input
                autoCapitalize="characters"
                autoComplete="off"
                id="board-key"
                maxLength={20}
                minLength={2}
                onChange={(event) => setKey(event.target.value.toUpperCase())}
                pattern="[A-Z0-9]{2,20}"
                placeholder="e.g. ROAD"
                required
                value={key}
              />
            </div>
          </div>
          {error && <p className="form-inline-error" role="alert">{error}</p>}
          <div className="form-actions">
            <button
              className="secondary-button"
              disabled={isCreating}
              onClick={onClose}
              type="button"
            >
              Cancel
            </button>
            <button className="primary-button" disabled={isCreating} type="submit">
              {isCreating ? 'Creating…' : 'Create Board'}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  );
}
