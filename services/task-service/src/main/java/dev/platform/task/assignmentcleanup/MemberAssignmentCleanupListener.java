package dev.platform.task.assignmentcleanup;

import java.io.IOException;
import java.util.HashSet;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.MemberAssignmentsCleanupRequested;
import dev.platform.shared.messaging.MessagingSupportConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Consumes the approved Membership cleanup request; the container ACKs after the recorder commits. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {
                "dev-platform.messaging.enabled",
                "dev-platform.messaging.consumers.enabled",
                "dev-platform.messaging.consumers.member-assignment-cleanup.retry-dlq-ready"
        },
        havingValue = "true")
public class MemberAssignmentCleanupListener {

    private static final TypeReference<EventEnvelope<MemberAssignmentsCleanupRequested>> EVENT_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final MemberAssignmentCleanupHandler handler;

    @RabbitListener(
            queues = "${dev-platform.messaging.consumers.member-assignment-cleanup.queue:task-service.member-assignment-cleanup}",
            containerFactory = MessagingSupportConfiguration.CONSUMER_LISTENER_CONTAINER_FACTORY,
            ackMode = "AUTO")
    public void receive(Message message) {
        EventEnvelope<MemberAssignmentsCleanupRequested> event;
        try {
            event = objectMapper.readValue(
                    message.getBody(), EVENT_TYPE);
            validate(event);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Rejecting invalid assignment cleanup message {} without requeue: {}",
                    message.getMessageProperties().getMessageId(), exception.getMessage());
            // Spring/container and the broker's shared DLX policy must route this poison message.
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid member assignment cleanup message", exception);
        }

        // Do not settle processing failures here. The shared listener factory owns bounded
        // retry/backoff and terminal DLQ routing; keep this consumer disabled until broker behavior
        // is verified for the target environment.
        // The recorder's transaction commits before this proxied call returns; AUTO mode then ACKs.
        handler.requestMemberAssignmentCleanup(event);
    }

    private static void validate(EventEnvelope<MemberAssignmentsCleanupRequested> event) {
        if (event == null || event.eventId() == null
                || !EventTypes.MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED.equals(event.eventType())
                || event.schemaVersion() != 1
                || !"PROJECT_MEMBERSHIP".equals(event.aggregateType())
                || event.aggregateVersion() < 1
                || event.payload() == null) {
            throw new IllegalArgumentException("Invalid member assignment cleanup envelope");
        }

        MemberAssignmentsCleanupRequested payload = event.payload();
        if (payload.operationId() == null || payload.projectId() == null || payload.projectId() < 1
                || payload.userId() == null || payload.membershipVersion() < 1 || payload.boardIds() == null
                || payload.boardIds().stream().anyMatch(id -> id == null || id < 1)
                || new HashSet<>(payload.boardIds()).size() != payload.boardIds().size()
                || !(payload.projectId() + ":" + payload.userId()).equals(event.aggregateId())) {
            throw new IllegalArgumentException("Invalid member assignment cleanup payload");
        }
    }
}
