package dev.platform.notification.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.CardAssigned;
import dev.platform.notification.application.command.ProcessTaskNotificationCommand;
import dev.platform.notification.application.port.in.ProcessTaskNotificationPort;
import dev.platform.notification.application.port.in.ProcessTaskNotificationPort.ProcessResult;

class TaskEventListenerTest {

    private ObjectMapper objectMapper;
    private ProcessTaskNotificationPort eventProcessor;
    private TaskEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        eventProcessor = mock(ProcessTaskNotificationPort.class);

        TaskNotificationEventMapper eventMapper =
                new TaskNotificationEventMapper(objectMapper);

        listener = new TaskEventListener(
                objectMapper,
                eventMapper,
                eventProcessor);
    }

    @Test
    void listenerDeserializesAndProcessesCardAssignedEvent()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();

        CardAssigned payload = new CardAssigned(
            1L,
            2L,
            42L,
        recipientUserId);

        EventEnvelope<CardAssigned> event = new EventEnvelope<>(
                eventId,
                EventTypes.CARD_ASSIGNED,
                1,
                "TASK_NOTIFICATION_INTENT",
                eventId.toString(),
                1L,
                OffsetDateTime.parse("2026-11-08T03:00:00Z"),
                payload);

        Message message = MessageBuilder
                .withBody(objectMapper.writeValueAsBytes(event))
                .build();

        when(eventProcessor.process(any()))
                .thenReturn(ProcessResult.CREATED);

        listener.onTaskEvent(message);

        ArgumentCaptor<ProcessTaskNotificationCommand> captor =
                ArgumentCaptor.forClass(
                        ProcessTaskNotificationCommand.class);

        verify(eventProcessor).process(captor.capture());

        ProcessTaskNotificationCommand command = captor.getValue();
        assertEquals(eventId, command.eventId());
        assertEquals(EventTypes.CARD_ASSIGNED, command.eventType());
        assertEquals(
            List.of(recipientUserId),
            command.recipientUserIds());
        assertEquals(42L, command.cardId());;
    }
    @Test
    void listenerPropagatesInvalidJsonForRetry() {
        Message message = MessageBuilder
            .withBody("not-json".getBytes(StandardCharsets.UTF_8))
            .build();

        assertThrows(
            IOException.class,
            () -> listener.onTaskEvent(message));

        verifyNoInteractions(eventProcessor);
    }
}