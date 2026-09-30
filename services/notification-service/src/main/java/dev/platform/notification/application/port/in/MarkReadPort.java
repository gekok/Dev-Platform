package dev.platform.notification.application.port.in;

import dev.platform.notification.application.command.MarkReadCommand;
import dev.platform.notification.domain.aggregate.NotificationAggregate;

// Cổng vào: đánh dấu đã đọc (check chủ sở hữu ở service kỳ 1).
public interface MarkReadPort {

    NotificationAggregate markRead(MarkReadCommand cmd);
}
