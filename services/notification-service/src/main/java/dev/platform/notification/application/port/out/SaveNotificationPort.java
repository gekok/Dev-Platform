package dev.platform.notification.application.port.out;

import dev.platform.notification.domain.repository.NotificationRepository;

// Cổng ra: lưu Notification (adapter JPA cài đặt).
public interface SaveNotificationPort extends NotificationRepository {
}
