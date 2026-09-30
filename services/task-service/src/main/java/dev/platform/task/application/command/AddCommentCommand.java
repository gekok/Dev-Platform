package dev.platform.task.application.command;

import java.util.List;
import java.util.UUID;

// Lệnh thêm bình luận vào thẻ.
public record AddCommentCommand(
        Long cardId,
        UUID authorId,
        String content,
        List<UUID> mentionedUserIds
) {
}
