import { useEffect, useId, useRef, useState } from 'react';
import { filterBoards } from '../boardSearch.js';

export default function BoardSelector({
  boards,
  currentBoard: activeBoard,
  currentBoardId,
  defaultBoardId,
  canCreate,
  isPreferenceLoading,
  isPreferenceReady,
  isPreferencePending,
  preferenceError,
  onRetryPreferences,
  isBoardListLoading,
  boardListError,
  onRetryBoardList,
  onSelectBoard,
  onSetDefault,
  onClearDefault,
  onCreateBoard,
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const menuId = useId();
  const searchId = useId();
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const searchRef = useRef(null);
  const currentBoard = boards.find((board) => Number(board.id) === Number(currentBoardId))
    ?? activeBoard;
  const matchingBoards = filterBoards(boards, search);

  useEffect(() => {
    if (!isOpen) return undefined;

    function closeOnOutsidePointer(event) {
      if (!containerRef.current?.contains(event.target)) {
        setIsOpen(false);
        setSearch('');
      }
    }

    function closeOnOutsideFocus(event) {
      if (!containerRef.current?.contains(event.target)) {
        setIsOpen(false);
        setSearch('');
      }
    }

    function closeOnEscape(event) {
      if (event.key !== 'Escape') return;
      setIsOpen(false);
      setSearch('');
      triggerRef.current?.focus();
    }

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    document.addEventListener('focusin', closeOnOutsideFocus);
    document.addEventListener('keydown', closeOnEscape);
    searchRef.current?.focus();

    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer);
      document.removeEventListener('focusin', closeOnOutsideFocus);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [isOpen]);

  function toggleMenu() {
    setSearch('');
    setIsOpen((open) => !open);
  }

  function handleSelectBoard(boardId) {
    setIsOpen(false);
    setSearch('');
    triggerRef.current?.focus();
    onSelectBoard(boardId);
  }

  function handleCreateBoard() {
    setIsOpen(false);
    setSearch('');
    triggerRef.current?.focus();
    onCreateBoard();
  }

  return (
    <div className="board-selector" ref={containerRef}>
      <h1 className="board-selector-heading" id="board-title">
        <button
          aria-controls={menuId}
          aria-expanded={isOpen}
          aria-haspopup="dialog"
          aria-label={`Choose a Board. Current Board: ${currentBoard?.name ?? 'Unknown'}`}
          className="board-selector-trigger"
          onClick={toggleMenu}
          ref={triggerRef}
          type="button"
        >
          <span>{currentBoard?.name ?? 'Board unavailable'}</span>
          <span aria-hidden="true" className="board-selector-chevron">⌄</span>
        </button>
      </h1>

      {isOpen && (
        <div
          aria-label="Select a Board"
          aria-modal="false"
          className="board-selector-popover"
          id={menuId}
          role="dialog"
        >
          <label className="visually-hidden" htmlFor={searchId}>Search Boards</label>
          <input
            autoComplete="off"
            className="board-selector-search"
            id={searchId}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by Board name or key"
            ref={searchRef}
            type="search"
            value={search}
          />

          {isPreferenceLoading && (
            <p aria-live="polite" className="board-selector-state">Loading saved Board…</p>
          )}
          {preferenceError && (
            <div className="board-selector-error" role="alert">
              <span>{preferenceError}</span>
              <button onClick={onRetryPreferences} type="button">Try again</button>
            </div>
          )}
          {boardListError && (
            <div className="board-selector-error" role="alert">
              <span>{boardListError}</span>
              <button onClick={onRetryBoardList} type="button">Try again</button>
            </div>
          )}

          {isBoardListLoading ? (
            <p aria-live="polite" className="board-selector-state">Loading Boards…</p>
          ) : matchingBoards.length > 0 ? (
            <ul aria-label="Boards in this Project" className="board-selector-list">
              {matchingBoards.map((board) => {
                const isCurrent = Number(board.id) === Number(currentBoardId);
                const isDefault = defaultBoardId != null &&
                  Number(board.id) === Number(defaultBoardId);

                return (
                  <li className="board-selector-result" key={board.id}>
                    <button
                      aria-current={isCurrent ? 'page' : undefined}
                      className="board-selector-open"
                      onClick={() => handleSelectBoard(board.id)}
                      type="button"
                    >
                      <span className="board-selector-result-name">{board.name}</span>
                      <span className="board-selector-result-meta">
                        <span>{board.key}</span>
                        {isCurrent && <span className="board-selector-current">Current</span>}
                        {isDefault && <span className="board-selector-default">Default</span>}
                      </span>
                    </button>
                    <button
                      aria-label={isDefault
                        ? `Clear ${board.name} as the default Board`
                        : `Set ${board.name} as the default Board`}
                      className="board-selector-default-action"
                      disabled={!isPreferenceReady || isPreferencePending}
                      onClick={() => {
                        if (isDefault) onClearDefault();
                        else onSetDefault(board.id);
                      }}
                      type="button"
                    >
                      {isPreferenceLoading
                        ? 'Loading…'
                        : !isPreferenceReady
                          ? 'Unavailable'
                          : isPreferencePending
                            ? 'Saving…'
                            : isDefault
                              ? 'Clear default'
                              : 'Set default'}
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : boardListError ? null : (
            <p aria-live="polite" className="board-selector-state">
              {search ? 'No Boards match your search.' : 'No Boards available.'}
            </p>
          )}

          {canCreate && (
            <div className="board-selector-footer">
              <button className="board-selector-create" onClick={handleCreateBoard} type="button">
                <span aria-hidden="true">+</span> New board
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
