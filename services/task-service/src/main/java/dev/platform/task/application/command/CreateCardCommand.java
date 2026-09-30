package dev.platform.task.application.command;

import java.time.OffsetDateTime;
import java.util.UUID;

// Lệnh tạo thẻ mới trong cột.
public record CreateCardCommand(
        Long boardId,
        Long columnId,
        String title,
        String description,
        UUID assigneeId,
        OffsetDateTime dueDate,
        UUID creatorId
) {
}
