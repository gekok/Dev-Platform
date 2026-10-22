import api from '../../../shared/services/axios.js';

export async function listMyNotifications({ unreadOnly = false } = {}) {
  const response = await api.get('/notifications/me', {
    params: unreadOnly ? { unreadOnly: true } : undefined,
  });
  return response.data;
}

export async function markNotificationRead(notificationId) {
  const response = await api.patch(`/notifications/${notificationId}/read`);
  return response.data;
}
