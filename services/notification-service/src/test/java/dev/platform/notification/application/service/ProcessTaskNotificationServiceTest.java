package dev.platform.notification.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.platform.contracts.EventTypes;
import dev.platform.notification.application.command.CreateNotificationCommand;
import dev.platform.notification.application.command.ProcessTaskNotificationCommand;
import dev.platform.notification.application.port.in.CreateNotificationPort;
import dev.platform.notification.application.port.in.ProcessTaskNotificationPort.ProcessResult;
import dev.platform.shared.messaging.InboxEventRecorder;

class ProcessTaskNotificationServiceTest {

    private static final String CONSUMER_NAME =
            "notification-service.task-events";

    private InboxEventRecorder inboxEventRecorder;
    private CreateNotificationPort createNotificationPort;
    private ProcessTaskNotificationService service;

    @BeforeEach
    void setUp() {
        inboxEventRecorder = mock(InboxEventRecorder.class);
        createNotificationPort = mock(CreateNotificationPort.class);
        service = new ProcessTaskNotificationService(
                inboxEventRecorder,
                createNotificationPort);
    }

    @Test
    void createsAssignedNotification() {
        UUID eventId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        ProcessTaskNotificationCommand command = command(
                eventId,
                EventTypes.CARD_ASSIGNED,
                1,
                List.of(recipientUserId));

        when(inboxEventRecorder.recordIfNew(
                CONSUMER_NAME,
                eventId,
                EventTypes.CARD_ASSIGNED))
                .thenReturn(true);

        ProcessResult result = service.process(command);

        assertEquals(ProcessResult.CREATED, result);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(
                        CreateNotificationCommand.class);
        verify(createNotificationPort).create(captor.capture());

        CreateNotificationCommand created = captor.getValue();
        assertEquals(recipientUserId, created.userId());
        assertEquals("ASSIGNED", created.type());
        assertEquals(1L, created.projectId());
        assertEquals(2L, created.boardId());
        assertEquals(42L, created.cardId());
    }

    @Test
    void createsMentionedNotificationForEveryRecipient() {
        UUID eventId = UUID.randomUUID();
        List<UUID> recipients = List.of(
                UUID.randomUUID(),
                UUID.randomUUID());

        ProcessTaskNotificationCommand command = command(
                eventId,
                EventTypes.COMMENT_MENTIONED,
                1,
                recipients);

        when(inboxEventRecorder.recordIfNew(
                CONSUMER_NAME,
                eventId,
                EventTypes.COMMENT_MENTIONED))
                .thenReturn(true);

        ProcessResult result = service.process(command);

        assertEquals(ProcessResult.CREATED, result);

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(
                        CreateNotificationCommand.class);
        verify(createNotificationPort, times(2))
                .create(captor.capture());

        List<CreateNotificationCommand> created =
                captor.getAllValues();

        assertEquals(
                recipients,
                created.stream()
                        .map(CreateNotificationCommand::userId)
                        .toList());
        assertEquals(
                List.of("MENTIONED", "MENTIONED"),
                created.stream()
                        .map(CreateNotificationCommand::type)
                        .toList());
    }

    @Test
    void doesNotCreateNotificationForDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        ProcessTaskNotificationCommand command = command(
                eventId,
                EventTypes.CARD_ASSIGNED,
                1,
                List.of(UUID.randomUUID()));

        when(inboxEventRecorder.recordIfNew(
                CONSUMER_NAME,
                eventId,
                EventTypes.CARD_ASSIGNED))
                .thenReturn(false);

        ProcessResult result = service.process(command);

        assertEquals(ProcessResult.DUPLICATE, result);
        verifyNoInteractions(createNotificationPort);
    }

    @Test
    void ignoresUnsupportedEventWithoutRecordingInbox() {
        ProcessTaskNotificationCommand command = command(
                UUID.randomUUID(),
                "card.deleted",
                1,
                List.of(UUID.randomUUID()));

        ProcessResult result = service.process(command);

        assertEquals(ProcessResult.IGNORED_UNSUPPORTED, result);
        verifyNoInteractions(
                inboxEventRecorder,
                createNotificationPort);
    }

    @Test
    void rejectsUnsupportedSchemaBeforeRecordingInbox() {
        ProcessTaskNotificationCommand command = command(
                UUID.randomUUID(),
                EventTypes.CARD_ASSIGNED,
                2,
                List.of(UUID.randomUUID()));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(command));

        verifyNoInteractions(
                inboxEventRecorder,
                createNotificationPort);
    }

    @Test
    void rejectsDuplicateRecipientsBeforeRecordingInbox() {
        UUID recipient = UUID.randomUUID();
        ProcessTaskNotificationCommand command = command(
                UUID.randomUUID(),
                EventTypes.COMMENT_MENTIONED,
                1,
                List.of(recipient, recipient));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.process(command));

        verifyNoInteractions(
                inboxEventRecorder,
                createNotificationPort);
    }

    @Test
    void propagatesCreateFailureForTransactionRollback() {
        UUID eventId = UUID.randomUUID();
        ProcessTaskNotificationCommand command = command(
                eventId,
                EventTypes.CARD_ASSIGNED,
                1,
                List.of(UUID.randomUUID()));

        when(inboxEventRecorder.recordIfNew(
                CONSUMER_NAME,
                eventId,
                EventTypes.CARD_ASSIGNED))
                .thenReturn(true);

        when(createNotificationPort.create(any()))
                .thenThrow(new IllegalStateException(
                        "database failure"));

        assertThrows(
                IllegalStateException.class,
                () -> service.process(command));

        verify(inboxEventRecorder).recordIfNew(
                CONSUMER_NAME,
                eventId,
                EventTypes.CARD_ASSIGNED);
        verify(createNotificationPort).create(any());
    }

    private ProcessTaskNotificationCommand command(
            UUID eventId,
            String eventType,
            int schemaVersion,
            List<UUID> recipientUserIds) {
        return new ProcessTaskNotificationCommand(
                eventId,
                eventType,
                schemaVersion,
                "TASK_NOTIFICATION_INTENT",
                eventId.toString(),
                1L,
                OffsetDateTime.parse(
                        "2026-11-08T03:00:00Z"),
                recipientUserIds,
                1L,
                2L,
                42L);
    }
}