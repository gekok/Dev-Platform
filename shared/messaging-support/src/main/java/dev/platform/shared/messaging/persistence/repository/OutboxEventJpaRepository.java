package dev.platform.shared.messaging.persistence.repository;

import dev.platform.shared.messaging.persistence.entity.OutboxEventEntity;
import dev.platform.shared.messaging.persistence.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status,
            OffsetDateTime now);
}
