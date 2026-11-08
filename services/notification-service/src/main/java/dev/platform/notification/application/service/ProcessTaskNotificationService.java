package dev.platform.notification.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.platform.contracts.EventTypes;
import dev.platform.notification.application.command.CreateNotificationCommand;
import dev.platform.notification.application.command.ProcessTaskNotificationCommand;
import dev.platform.notification.application.port.in.CreateNotificationPort;
import dev.platform.notification.application.port.in.ProcessTaskNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate.TypeValue;
import dev.platform.shared.messaging.InboxEventRecorder;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProcessTaskNotificationService
        implements ProcessTaskNotificationPort {

    private static final String CONSUMER_NAME =
            "notification-service.task-events";
    private static final String AGGREGATE_TYPE =
            "TASK_NOTIFICATION_INTENT";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final long SUPPORTED_AGGREGATE_VERSION = 1L;

    private final InboxEventRecorder inboxEventRecorder;
    private final CreateNotificationPort createNotificationPort;

    @Override
    @Transactional
    public ProcessResult process(ProcessTaskNotificationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command khong duoc null");
        }
        if (command.eventType() == null
                || command.eventType().isBlank()) {
            throw new IllegalArgumentException(
                    "eventType khong duoc trong");
        }

        if (!isSupportedEvent(command.eventType())) {
            return ProcessResult.IGNORED_UNSUPPORTED;
        }

        validate(command);

        boolean firstProcessing = inboxEventRecorder.recordIfNew(
                CONSUMER_NAME,
                command.eventId(),
                command.eventType());

        if (!firstProcessing) {
            return ProcessResult.DUPLICATE;
        }

        NotificationMapping mapping = mappingFor(command);

        for (UUID recipientUserId : command.recipientUserIds()) {
            createNotificationPort.create(new CreateNotificationCommand(
                    recipientUserId,
                    command.projectId(),
                    mapping.type().name(),
                    mapping.title(),
                    command.cardId(),
                    command.boardId()));
        }

        return ProcessResult.CREATED;
    }

    private boolean isSupportedEvent(String eventType) {
        return EventTypes.CARD_ASSIGNED.equals(eventType)
                || EventTypes.COMMENT_MENTIONED.equals(eventType);
    }

    private NotificationMapping mappingFor(
            ProcessTaskNotificationCommand command) {
        return switch (command.eventType()) {
            case EventTypes.CARD_ASSIGNED ->
                    new NotificationMapping(
                            TypeValue.ASSIGNED,
                            "Bạn được giao công việc #"
                                    + command.cardId());

            case EventTypes.COMMENT_MENTIONED ->
                    new NotificationMapping(
                            TypeValue.MENTIONED,
                            "Bạn được nhắc đến trong công việc #"
                                    + command.cardId());

            default -> throw new IllegalStateException(
                    "eventType khong duoc ho tro");
        };
    }

    private void validate(ProcessTaskNotificationCommand command) {
        if (command.eventId() == null) {
            throw new IllegalArgumentException(
                    "eventId khong duoc null");
        }
        if (command.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "schemaVersion khong duoc ho tro");
        }
        if (!AGGREGATE_TYPE.equals(command.aggregateType())) {
            throw new IllegalArgumentException(
                    "aggregateType khong hop le");
        }
        if (!command.eventId().toString().equals(
                command.aggregateId())) {
            throw new IllegalArgumentException(
                    "aggregateId phai khop eventId");
        }
        if (command.aggregateVersion()
                != SUPPORTED_AGGREGATE_VERSION) {
            throw new IllegalArgumentException(
                    "aggregateVersion khong duoc ho tro");
        }
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "occurredAt khong duoc null");
        }
        if (command.projectId() == null
                || command.boardId() == null
                || command.cardId() == null) {
            throw new IllegalArgumentException(
                    "projectId, boardId va cardId khong duoc null");
        }

        validateRecipients(command.recipientUserIds());
    }

    private void validateRecipients(List<UUID> recipientUserIds) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "recipientUserIds khong duoc rong");
        }
        if (recipientUserIds.stream().anyMatch(
                recipient -> recipient == null)) {
            throw new IllegalArgumentException(
                    "recipientUserIds khong duoc chua null");
        }
        if (new HashSet<>(recipientUserIds).size()
                != recipientUserIds.size()) {
            throw new IllegalArgumentException(
                    "recipientUserIds khong duoc trung lap");
        }
    }

    private record NotificationMapping(
            TypeValue type,
            String title) {
    }
}