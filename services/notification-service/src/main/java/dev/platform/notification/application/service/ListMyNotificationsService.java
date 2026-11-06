package dev.platform.notification.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.platform.notification.application.port.in.ListMyNotificationsPort;
import dev.platform.notification.application.port.out.SaveNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListMyNotificationsService implements ListMyNotificationsPort {

    private final SaveNotificationPort notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationAggregate> listMine(UUID userId, boolean unreadOnly) {
        if (userId == null) {
            throw new IllegalArgumentException("userId khong duoc null");
        }

        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadFalse(userId);
        }

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
