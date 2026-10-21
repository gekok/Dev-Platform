import { useEffect, useRef, useState } from 'react';

function getInitials(fullName) {
  return fullName
    ?.split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
}

export default function AccountMenu({ user, onSignOut }) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const signOutRef = useRef(null);

  useEffect(() => {
    if (!isOpen) return undefined;

    function closeOnOutsidePointer(event) {
      if (!containerRef.current?.contains(event.target)) setIsOpen(false);
    }

    function closeOnEscape(event) {
      if (event.key !== 'Escape') return;
      setIsOpen(false);
      triggerRef.current?.focus();
    }

    function closeOnOutsideFocus(event) {
      if (!containerRef.current?.contains(event.target)) setIsOpen(false);
    }

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    document.addEventListener('keydown', closeOnEscape);
    document.addEventListener('focusin', closeOnOutsideFocus);
    signOutRef.current?.focus();

    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer);
      document.removeEventListener('keydown', closeOnEscape);
      document.removeEventListener('focusin', closeOnOutsideFocus);
    };
  }, [isOpen]);

  function handleSignOut() {
    setIsOpen(false);
    onSignOut();
  }

  return (
    <div className="account-menu" ref={containerRef}>
      <button
        aria-controls="account-menu-panel"
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        aria-label={`Account menu for ${user?.fullName ?? 'current user'}`}
        className="account-menu-trigger"
        onClick={() => setIsOpen((open) => !open)}
        ref={triggerRef}
        type="button"
      >
        <span aria-hidden="true" className="avatar">
          {getInitials(user?.fullName) || 'U'}
        </span>
      </button>

      {isOpen && (
        <div
          aria-label="Account menu"
          aria-modal="false"
          className="account-menu-popover"
          id="account-menu-panel"
          role="dialog"
        >
          <div className="account-menu-details">
            <strong>{user?.fullName}</strong>
            <small>{user?.email}</small>
          </div>
          <div aria-hidden="true" className="account-menu-divider" />
          <button
            className="account-menu-sign-out"
            onClick={handleSignOut}
            ref={signOutRef}
            type="button"
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
