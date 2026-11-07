package dev.platform.task.infrastructure.messaging;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.CardAssigned;
import dev.platform.contracts.event.CommentMentioned;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.task.application.port.out.TaskNotificationEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Adapts Task notification intents to the shared transactional Outbox contract. */
@Component
@RequiredArgsConstructor
public class TaskNotificationOutboxAdapter implements TaskNotificationEventPort {

    private static final String AGGREGATE_TYPE = "TASK_NOTIFICATION_INTENT";
    private static final int SCHEMA_VERSION = 1;
    private static final long AGGREGATE_VERSION = 1;

    private final OutboxEventWriter outbox;

    @Override
    public void cardAssigned(Long projectId, Long boardId, Long cardId, UUID recipientUserId) {
        UUID eventId = UUID.randomUUID();
        append(eventId, EventTypes.CARD_ASSIGNED,
                new CardAssigned(projectId, boardId, cardId, recipientUserId));
    }

    @Override
    public void commentMentioned(Long projectId, Long boardId, Long cardId, Long commentId,
            List<UUID> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        append(eventId, EventTypes.COMMENT_MENTIONED,
                new CommentMentioned(projectId, boardId, cardId, commentId, recipientUserIds));
    }

    private void append(UUID eventId, String eventType, Object payload) {
        outbox.append(new EventEnvelope<>(eventId, eventType, SCHEMA_VERSION, AGGREGATE_TYPE,
                eventId.toString(), AGGREGATE_VERSION, OffsetDateTime.now(ZoneOffset.UTC), payload));
    }
}
