package dev.platform.notification.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.platform.notification.domain.aggregate.NotificationAggregate;

// Hợp đồng lưu trữ Notification cho application dùng.
public interface NotificationRepository {

    NotificationAggregate save(NotificationAggregate n);

    Optional<NotificationAggregate> findById(Long id);

    List<NotificationAggregate> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationAggregate> findByUserIdAndReadFalse(UUID userId);
}
