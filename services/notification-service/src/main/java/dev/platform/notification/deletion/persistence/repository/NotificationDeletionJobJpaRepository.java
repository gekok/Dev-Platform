package dev.platform.notification.deletion.persistence.repository;

import dev.platform.notification.deletion.persistence.entity.NotificationDeletionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationDeletionJobJpaRepository extends JpaRepository<NotificationDeletionJobEntity, UUID> {
}
