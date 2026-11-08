package dev.platform.notification.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.CardAssigned;
import dev.platform.contracts.event.CommentMentioned;
import dev.platform.notification.application.command.ProcessTaskNotificationCommand;

class TaskNotificationEventMapperTest {

    private ObjectMapper objectMapper;
    private TaskNotificationEventMapper mapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new TaskNotificationEventMapper(objectMapper);
    }

    @Test
    void mapsCardAssignedEventToCommand() {
        UUID eventId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();

        CardAssigned payload = new CardAssigned(
                1L,
                2L,
                42L,
                recipientUserId);

        ProcessTaskNotificationCommand command =
                mapper.toCommand(envelope(
                        eventId,
                        EventTypes.CARD_ASSIGNED,
                        payload));

        assertEquals(eventId, command.eventId());
        assertEquals(EventTypes.CARD_ASSIGNED, command.eventType());
        assertEquals(1, command.schemaVersion());
        assertEquals(
                "TASK_NOTIFICATION_INTENT",
                command.aggregateType());
        assertEquals(eventId.toString(), command.aggregateId());
        assertEquals(1L, command.aggregateVersion());
        assertEquals(
                List.of(recipientUserId),
                command.recipientUserIds());
        assertEquals(1L, command.projectId());
        assertEquals(2L, command.boardId());
        assertEquals(42L, command.cardId());
    }

    @Test
    void mapsCommentMentionedEventToCommand() {
        UUID eventId = UUID.randomUUID();
        UUID firstRecipient = UUID.randomUUID();
        UUID secondRecipient = UUID.randomUUID();

        CommentMentioned payload = new CommentMentioned(
                1L,
                2L,
                42L,
                100L,
                List.of(firstRecipient, secondRecipient));

        ProcessTaskNotificationCommand command =
                mapper.toCommand(envelope(
                        eventId,
                        EventTypes.COMMENT_MENTIONED,
                        payload));

        assertEquals(
                EventTypes.COMMENT_MENTIONED,
                command.eventType());
        assertEquals(
                List.of(firstRecipient, secondRecipient),
                command.recipientUserIds());
        assertEquals(1L, command.projectId());
        assertEquals(2L, command.boardId());
        assertEquals(42L, command.cardId());
    }

    @Test
    void rejectsCardAssignedWithoutRecipient() {
        CardAssigned payload = new CardAssigned(
                1L,
                2L,
                42L,
                null);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toCommand(envelope(
                        UUID.randomUUID(),
                        EventTypes.CARD_ASSIGNED,
                        payload)));
    }

    @Test
    void rejectsCommentMentionedWithoutCommentId() {
        CommentMentioned payload = new CommentMentioned(
                1L,
                2L,
                42L,
                null,
                List.of(UUID.randomUUID()));

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toCommand(envelope(
                        UUID.randomUUID(),
                        EventTypes.COMMENT_MENTIONED,
                        payload)));
    }

    @Test
    void mapsUnsupportedEventWithoutReadingPayload() {
        UUID eventId = UUID.randomUUID();

        EventEnvelope<JsonNode> event = new EventEnvelope<>(
                eventId,
                "card.deleted",
                1,
                "TASK_NOTIFICATION_INTENT",
                eventId.toString(),
                1L,
                OffsetDateTime.parse("2026-11-08T03:00:00Z"),
                objectMapper.createObjectNode());

        ProcessTaskNotificationCommand command =
                mapper.toCommand(event);

        assertEquals("card.deleted", command.eventType());
        assertNull(command.recipientUserIds());
        assertNull(command.projectId());
        assertNull(command.boardId());
        assertNull(command.cardId());
    }

    private EventEnvelope<JsonNode> envelope(
            UUID eventId,
            String eventType,
            Object payload) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                1,
                "TASK_NOTIFICATION_INTENT",
                eventId.toString(),
                1L,
                OffsetDateTime.parse("2026-11-08T03:00:00Z"),
                objectMapper.valueToTree(payload));
    }
}