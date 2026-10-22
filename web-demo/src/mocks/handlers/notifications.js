import { http, HttpResponse } from 'msw';
import { authenticateRequest, errorResponse, parsePositiveId } from './utils.js';

function mapNotification(notification) {
  return {
    id: notification.id,
    type: notification.type,
    title: notification.title,
    cardId: notification.cardId,
    boardId: notification.boardId,
    read: notification.read,
    createdAt: notification.createdAt,
  };
}

export function createNotificationHandlers(repository, resolveUserFromToken) {
  return [
    http.get('*/api/v1/notifications/me', ({ request }) => {
      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const unreadOnly = new URL(request.url).searchParams.get('unreadOnly') === 'true';
      const notifications = authentication.data.notifications
        .filter((notification) =>
          notification.recipientId === authentication.user.id &&
          (!unreadOnly || !notification.read),
        )
        .sort((left, right) =>
          right.createdAt.localeCompare(left.createdAt) || right.id - left.id,
        );

      return HttpResponse.json(notifications.map(mapNotification));
    }),
    http.patch('*/api/v1/notifications/:notificationId/read', ({ request, params }) => {
      const notificationId = parsePositiveId(params.notificationId);
      if (!notificationId) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Notification not found');
      }

      const authentication = authenticateRequest(
        request,
        repository,
        resolveUserFromToken,
      );
      if (authentication.response) return authentication.response;

      const notification = authentication.data.notifications.find(
        (item) => item.id === notificationId,
      );
      if (!notification) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Notification not found');
      }
      if (notification.recipientId !== authentication.user.id) {
        return errorResponse(
          request,
          403,
          'FORBIDDEN',
          'Only the Notification recipient can mark it as read',
        );
      }

      const result = repository.transact((draft) => {
        const current = draft.notifications.find((item) => item.id === notificationId);
        if (!current) return { missing: true };
        if (current.recipientId !== authentication.user.id) return { forbidden: true };
        current.read = true;
        return { notification: mapNotification(current) };
      });

      if (result.missing) {
        return errorResponse(request, 404, 'NOT_FOUND', 'Notification not found');
      }
      if (result.forbidden) {
        return errorResponse(
          request,
          403,
          'FORBIDDEN',
          'Only the Notification recipient can mark it as read',
        );
      }

      return HttpResponse.json(result.notification);
    }),
  ];
}
