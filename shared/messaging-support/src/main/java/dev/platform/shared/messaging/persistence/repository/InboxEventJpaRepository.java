package dev.platform.shared.messaging.persistence.repository;

import dev.platform.shared.messaging.persistence.entity.InboxEventEntity;
import dev.platform.shared.messaging.persistence.entity.InboxEventId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventJpaRepository extends JpaRepository<InboxEventEntity, InboxEventId> {
}
