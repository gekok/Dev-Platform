package dev.platform.notification.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.platform.notification.infrastructure.persistence.entity.NotificationEntity;

// Truy vấn bảng notifications.
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationEntity> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId);
}
