package dev.platform.task.assignmentcleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.MemberAssignmentsCleanupRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

class MemberAssignmentCleanupListenerTest {

    private ObjectMapper objectMapper;
    private MemberAssignmentCleanupHandler handler;
    private MemberAssignmentCleanupListener listener;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = mock(MemberAssignmentCleanupHandler.class);
        listener = new MemberAssignmentCleanupListener(objectMapper, handler);
        userId = UUID.randomUUID();
    }

    @Test
    void automaticAckModeReturnsOnlyAfterRecorderReturns() throws Exception {
        EventEnvelope<MemberAssignmentsCleanupRequested> event = validEvent();

        listener.receive(message(objectMapper.writeValueAsBytes(event)));

        verify(handler).requestMemberAssignmentCleanup(event);
        RabbitListener mapping = MemberAssignmentCleanupListener.class
                .getMethod("receive", Message.class)
                .getAnnotation(RabbitListener.class);
        assertEquals("AUTO", mapping.ackMode());
    }

    @Test
    void rejectsMalformedJsonWithoutRequeueSignal() throws Exception {
        Message message = message("{not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(message));

        verifyNoInteractions(handler);
    }

    @Test
    void rejectsInvalidContractWithoutRequeueSignal() throws Exception {
        EventEnvelope<MemberAssignmentsCleanupRequested> event = validEvent();
        var invalidEvent = new EventEnvelope<>(event.eventId(), "unknown.event", event.schemaVersion(),
                event.aggregateType(), event.aggregateId(), event.aggregateVersion(), event.occurredAt(),
                event.payload());

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.receive(message(objectMapper.writeValueAsBytes(invalidEvent))));

        verifyNoInteractions(handler);
    }

    @Test
    void letsRecorderFailureReachSharedRetryPolicyWithoutAcknowledging() throws Exception {
        EventEnvelope<MemberAssignmentsCleanupRequested> event = validEvent();
        IllegalStateException databaseUnavailable = new IllegalStateException("database unavailable");
        doThrow(databaseUnavailable).when(handler).requestMemberAssignmentCleanup(any());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> listener.receive(message(objectMapper.writeValueAsBytes(event))));

        assertSame(databaseUnavailable, thrown);
    }

    @Test
    void listenerCanProcessValidDeliveryAfterRejectingPoison() throws Exception {
        Message poison = message("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        EventEnvelope<MemberAssignmentsCleanupRequested> event = validEvent();

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(poison));
        listener.receive(message(objectMapper.writeValueAsBytes(event)));

        verify(handler).requestMemberAssignmentCleanup(event);
    }

    private EventEnvelope<MemberAssignmentsCleanupRequested> validEvent() {
        var payload = new MemberAssignmentsCleanupRequested(
                UUID.randomUUID(), 12L, userId, List.of(3L, 4L), 7L);
        return new EventEnvelope<>(UUID.randomUUID(), EventTypes.MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED,
                1, "PROJECT_MEMBERSHIP", "12:" + userId, 9L, OffsetDateTime.now(ZoneOffset.UTC), payload);
    }

    private static Message message(byte[] body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("cleanup-message-1");
        return new Message(body, properties);
    }
}
