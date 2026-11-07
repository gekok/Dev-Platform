package dev.platform.notification.application.service;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.platform.notification.application.command.MarkReadCommand;
import dev.platform.notification.application.port.in.MarkReadPort;
import dev.platform.notification.application.port.out.SaveNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarkReadService implements MarkReadPort {

    private final SaveNotificationPort notificationRepository;

    @Override
    @Transactional
    public NotificationAggregate markRead(MarkReadCommand cmd) {
        if (cmd == null || cmd.id() == null || cmd.userId() == null) {
            throw new IllegalArgumentException("command, id va userId khong duoc null");
        }

        NotificationAggregate notification = notificationRepository.findById(cmd.id())
                .orElseThrow(() -> new NoSuchElementException("Notification khong ton tai"));

        if (!cmd.userId().equals(notification.getUserId())) {
            throw new SecurityException("Notification khong thuoc ve user nay");
        }

        notification.markRead();
        return notificationRepository.save(notification);
    }
}
