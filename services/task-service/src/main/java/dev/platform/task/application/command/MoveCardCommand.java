package dev.platform.task.application.command;

// Lệnh kéo thẻ qua cột khác / đổi vị trí.
public record MoveCardCommand(
        Long cardId,
        Long toColumnId,
        int toPosition
) {
}
