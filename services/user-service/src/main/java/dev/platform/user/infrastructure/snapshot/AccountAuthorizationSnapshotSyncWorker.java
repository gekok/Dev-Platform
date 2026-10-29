package dev.platform.user.infrastructure.snapshot;

import java.time.OffsetDateTime;

import dev.platform.user.infrastructure.persistence.entity.AccountAuthorizationSnapshotSyncJobEntity;
import dev.platform.user.infrastructure.persistence.repository.AccountAuthorizationSnapshotSyncJobJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-platform.snapshot-sync.user.enabled", havingValue = "true")
public class AccountAuthorizationSnapshotSyncWorker {

    private final AccountAuthorizationSnapshotSyncJobJpaRepository jobs;
    private final RedisAccountAuthorizationSnapshotStore redisStore;

    @Scheduled(fixedDelayString = "${dev-platform.snapshot-sync.user.fixed-delay-ms:1000}")
    @Transactional
    public void syncDueSnapshots() {
        OffsetDateTime now = OffsetDateTime.now();
        for (AccountAuthorizationSnapshotSyncJobEntity job : jobs
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc("PENDING", now)) {
            try {
                redisStore.apply(job.getAccountId(), job.getSnapshotJson());
                job.markSynced(OffsetDateTime.now());
            } catch (RuntimeException exception) {
                job.scheduleRetry(OffsetDateTime.now(), exception.getMessage());
            }
        }
    }
}
