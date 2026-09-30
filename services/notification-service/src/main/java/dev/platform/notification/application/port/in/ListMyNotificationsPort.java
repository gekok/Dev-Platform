package dev.platform.notification.application.port.in;

import java.util.List;
import java.util.UUID;

import dev.platform.notification.domain.aggregate.NotificationAggregate;

// Cổng vào: đọc thông báo của tôi.
public interface ListMyNotificationsPort {

    List<NotificationAggregate> listMine(UUID userId, boolean unreadOnly);
}
