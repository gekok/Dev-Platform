package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.AccountAuthorizationSnapshotSyncJobEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountAuthorizationSnapshotSyncJobJpaRepository
    extends JpaRepository<AccountAuthorizationSnapshotSyncJobEntity, UUID> {

  List<AccountAuthorizationSnapshotSyncJobEntity>
      findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
          String status, OffsetDateTime now);
}
