package dev.platform.notification.application.port.in;

import dev.platform.notification.application.command.ProcessTaskNotificationCommand;

// Cổng vào xử lý task event và tạo Notification theo cơ chế Inbox chống trùng.
public interface ProcessTaskNotificationPort {

    ProcessResult process(ProcessTaskNotificationCommand command);

    enum ProcessResult {
        CREATED,
        DUPLICATE,
        IGNORED_UNSUPPORTED
    }
}