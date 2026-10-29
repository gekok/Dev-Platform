package dev.platform.notification.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Body internal cho task-service gọi sang (kỳ 1).
public record CreateNotificationRequest(
        @NotNull UUID userId,
        Long projectId,
        @NotBlank String type,
        @NotBlank String title,
        Long cardId,
        Long boardId
) {
}
