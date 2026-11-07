package dev.platform.contracts.event;

import java.util.UUID;

/** Notification intent for a newly assigned Card recipient. */
public record CardAssigned(
        Long projectId,
        Long boardId,
        Long cardId,
        UUID recipientUserId) {
}
