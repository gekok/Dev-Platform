import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import {
  useMarkNotificationRead,
  useNotifications,
} from '../hooks/useNotifications.js';

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Time unavailable';
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

function getErrorMessage(error, fallback) {
  return error.response?.data?.message ?? fallback;
}

function getTypeLabel(type) {
  const labels = {
    ASSIGNED: 'Assigned',
    MENTIONED: 'Mentioned',
    MOVED: 'Moved',
  };
  return labels[type] ?? 'Update';
}

export default function NotificationPanel({ userId }) {
  const location = useLocation();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef(null);
  const triggerRef = useRef(null);
  const notificationsQuery = useNotifications(userId);
  const markReadMutation = useMarkNotificationRead(userId);
  const notifications = notificationsQuery.data ?? [];
  const unreadCount = notifications.reduce(
    (total, notification) => total + Number(!notification.read),
    0,
  );

  useEffect(() => {
    setIsOpen(false);
  }, [location.key]);

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

    function closeWhenAnotherDialogReceivesFocus(event) {
      const focusedDialog = event.target instanceof Element
        ? event.target.closest('[role="dialog"]')
        : null;
      if (focusedDialog && !containerRef.current?.contains(focusedDialog)) {
        setIsOpen(false);
      }
    }

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    document.addEventListener('keydown', closeOnEscape);
    document.addEventListener('focusin', closeWhenAnotherDialogReceivesFocus);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer);
      document.removeEventListener('keydown', closeOnEscape);
      document.removeEventListener('focusin', closeWhenAnotherDialogReceivesFocus);
    };
  }, [isOpen]);

  return (
    <div className="notification-control" ref={containerRef}>
      <button
        aria-controls="notification-panel"
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        className="icon-button"
        onClick={() => setIsOpen((open) => !open)}
        ref={triggerRef}
        type="button"
      >
        <span aria-hidden="true">♧</span>
        {unreadCount > 0 && <span aria-hidden="true" className="notification-dot" />}
      </button>

      <section
        aria-hidden={!isOpen}
        aria-label="Notifications"
        aria-modal="false"
        className="notification-panel"
        hidden={!isOpen}
        id="notification-panel"
        role="dialog"
      >
          <header className="notification-panel-header">
            <div>
              <span className="eyebrow">ACTIVITY</span>
              <h2>Notifications</h2>
            </div>
            <span className="notification-unread-count">{unreadCount} unread</span>
          </header>

          {notificationsQuery.isPending && (
            <p aria-live="polite" className="notification-panel-state">Loading notifications…</p>
          )}
          {notificationsQuery.isError && (
            <div className="notification-panel-state" role="alert">
              <span>{getErrorMessage(notificationsQuery.error, 'Notifications could not be loaded.')}</span>
              <button className="notification-retry" onClick={() => notificationsQuery.refetch()} type="button">
                Try again
              </button>
            </div>
          )}
          {notificationsQuery.isSuccess && notifications.length === 0 && (
            <p className="notification-panel-state">You’re all caught up.</p>
          )}
          {notificationsQuery.isSuccess && notifications.length > 0 && (
            <ol className="notification-list">
              {notifications.map((notification) => (
                <li
                  className={`notification-item${notification.read ? '' : ' notification-item-unread'}`}
                  key={notification.id}
                >
                  <div className="notification-item-topline">
                    <span className="notification-type">{getTypeLabel(notification.type)}</span>
                    <span className={`notification-read-state${notification.read ? '' : ' notification-read-state-new'}`}>
                      {notification.read ? 'Read' : 'New'}
                    </span>
                  </div>
                  <h3>{notification.title}</h3>
                  <time dateTime={notification.createdAt}>{formatDateTime(notification.createdAt)}</time>
                  {markReadMutation.isError && markReadMutation.variables === notification.id && (
                    <p className="notification-error" role="alert">
                      {getErrorMessage(markReadMutation.error, 'This Notification could not be updated.')}
                    </p>
                  )}
                  {!notification.read && (
                    <button
                      className="notification-mark-read"
                      disabled={markReadMutation.isPending && markReadMutation.variables === notification.id}
                      onClick={() => markReadMutation.mutate(notification.id)}
                      type="button"
                    >
                      Mark as read
                    </button>
                  )}
                </li>
              ))}
            </ol>
          )}
      </section>
    </div>
  );
}
