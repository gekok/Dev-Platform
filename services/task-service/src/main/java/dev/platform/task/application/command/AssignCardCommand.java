package dev.platform.task.application.command;

import java.util.UUID;

// Lệnh gán người làm cho thẻ.
public record AssignCardCommand(
        Long projectId,
        Long cardId,
        UUID assigneeId,
        Long assigneeMembershipVersion,
        UUID actorId
) {
}
