package dev.platform.task.application.command;

import java.util.UUID;

// Lệnh gán người làm cho thẻ.
public record AssignCardCommand(
        Long cardId,
        UUID assigneeId,
        UUID actorId
) {
}
