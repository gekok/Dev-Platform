package dev.platform.notification.application.port.in;

import dev.platform.notification.application.command.CreateNotificationCommand;
import dev.platform.notification.domain.aggregate.NotificationAggregate;

// Cổng vào: tạo thông báo từ event task-service (internal).
public interface CreateNotificationPort {

    NotificationAggregate create(CreateNotificationCommand cmd);
}
