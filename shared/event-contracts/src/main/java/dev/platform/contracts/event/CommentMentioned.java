package dev.platform.contracts.event;

import java.util.List;
import java.util.UUID;

/** Notification intent for the distinct recipients mentioned in a committed Comment. */
public record CommentMentioned(
        Long projectId,
        Long boardId,
        Long cardId,
        Long commentId,
        List<UUID> recipientUserIds) {

    public CommentMentioned {
        recipientUserIds = List.copyOf(recipientUserIds);
    }
}
