package dev.platform.task.application.port.out;

import java.util.List;
import java.util.UUID;

/** Durable notification intents written to the Task database Outbox. */
public interface TaskNotificationEventPort {
    void cardAssigned(Long projectId, Long boardId, Long cardId, UUID recipientUserId);

    void commentMentioned(Long projectId, Long boardId, Long cardId, Long commentId,
            List<UUID> recipientUserIds);
}
