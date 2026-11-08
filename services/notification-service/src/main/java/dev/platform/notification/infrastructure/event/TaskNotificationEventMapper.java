package dev.platform.notification.infrastructure.event;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.CardAssigned;
import dev.platform.contracts.event.CommentMentioned;
import dev.platform.notification.application.command.ProcessTaskNotificationCommand;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TaskNotificationEventMapper {

    private final ObjectMapper objectMapper;

    public ProcessTaskNotificationCommand toCommand(
            EventEnvelope<JsonNode> event) {
        if (event == null) {
            throw new IllegalArgumentException("event khong duoc null");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new IllegalArgumentException("eventType khong duoc trong");
        }

        return switch (event.eventType()) {
            case EventTypes.CARD_ASSIGNED -> fromAssigned(event);
            case EventTypes.COMMENT_MENTIONED -> fromMentioned(event);
            default -> command(event, null, null, null, null);
        };
    }

    private ProcessTaskNotificationCommand fromAssigned(
            EventEnvelope<JsonNode> event) {
        CardAssigned payload = readPayload(event, CardAssigned.class);

        if (payload.recipientUserId() == null) {
            throw new IllegalArgumentException(
                    "recipientUserId khong duoc null");
        }

        return command(
                event,
                List.of(payload.recipientUserId()),
                payload.projectId(),
                payload.boardId(),
                payload.cardId());
    }

    private ProcessTaskNotificationCommand fromMentioned(
            EventEnvelope<JsonNode> event) {
        CommentMentioned payload =
                readPayload(event, CommentMentioned.class);

        if (payload.commentId() == null) {
            throw new IllegalArgumentException(
                    "commentId khong duoc null");
        }

        return command(
                event,
                payload.recipientUserIds(),
                payload.projectId(),
                payload.boardId(),
                payload.cardId());
    }

    private <T> T readPayload(
            EventEnvelope<JsonNode> event,
            Class<T> payloadType) {
        if (event.payload() == null || event.payload().isNull()) {
            throw new IllegalArgumentException("payload khong duoc null");
        }

        try {
            return objectMapper.treeToValue(event.payload(), payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "payload khong dung contract",
                    exception);
        }
    }

    private ProcessTaskNotificationCommand command(
            EventEnvelope<JsonNode> event,
            List<UUID> recipientUserIds,
            Long projectId,
            Long boardId,
            Long cardId) {
        return new ProcessTaskNotificationCommand(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.aggregateType(),
                event.aggregateId(),
                event.aggregateVersion(),
                event.occurredAt(),
                recipientUserIds,
                projectId,
                boardId,
                cardId);
    }
}