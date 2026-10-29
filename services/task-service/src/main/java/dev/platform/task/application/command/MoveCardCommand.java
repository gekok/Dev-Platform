package dev.platform.task.application.command;

import java.util.UUID;

// Lệnh kéo thẻ qua cột khác / đổi vị trí.
public record MoveCardCommand(
        Long projectId,
        Long cardId,
        Long toColumnId,
        int toPosition,
        UUID actorId
) {
}
