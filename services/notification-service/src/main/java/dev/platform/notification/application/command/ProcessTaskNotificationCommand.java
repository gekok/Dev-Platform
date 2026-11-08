package dev.platform.notification.application.command;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Dữ liệu event được listener chuẩn hóa trước khi tạo Notification.
public record ProcessTaskNotificationCommand(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        List<UUID> recipientUserIds,
        Long projectId,
        Long boardId,
        Long cardId
) {
    public ProcessTaskNotificationCommand {
        recipientUserIds = recipientUserIds == null
                ? null
                : List.copyOf(recipientUserIds);
    }
}