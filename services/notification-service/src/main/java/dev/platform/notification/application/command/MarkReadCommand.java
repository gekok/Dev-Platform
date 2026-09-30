package dev.platform.notification.application.command;

import java.util.UUID;

// Lệnh đánh dấu đã đọc, kèm userId để check chủ sở hữu.
public record MarkReadCommand(
        Long id,
        UUID userId
) {
}
