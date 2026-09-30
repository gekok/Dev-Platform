package dev.platform.notification.application.command;

import java.util.UUID;

// Lệnh tạo thông báo (chỉ service gọi, không cho client).
public record CreateNotificationCommand(
        UUID userId,
        String type,
        String title,
        Long cardId,
        Long boardId
) {
}
