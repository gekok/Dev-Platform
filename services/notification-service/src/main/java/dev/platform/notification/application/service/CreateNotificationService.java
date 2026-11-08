package dev.platform.notification.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.platform.notification.application.command.CreateNotificationCommand;
import dev.platform.notification.application.port.in.CreateNotificationPort;
import dev.platform.notification.application.port.out.SaveNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate;
import dev.platform.notification.domain.aggregate.NotificationAggregate.TypeValue;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreateNotificationService implements CreateNotificationPort {

    private final SaveNotificationPort notificationRepository;

    @Override
    @Transactional
    public NotificationAggregate create(CreateNotificationCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("command khong duoc null");
        }

        if (cmd.type() == null || cmd.type().isBlank()) {
            throw new IllegalArgumentException("type khong duoc de trong");
        }

        TypeValue type;
        try {
            type = TypeValue.valueOf(cmd.type().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "type phai la ASSIGNED, MENTIONED hoac MOVED");
        }

        NotificationAggregate notification = NotificationAggregate.create(
                cmd.userId(),
                cmd.projectId(),
                type,
                cmd.title(),
                cmd.cardId(),
                cmd.boardId());

        return notificationRepository.save(notification);
    }
}