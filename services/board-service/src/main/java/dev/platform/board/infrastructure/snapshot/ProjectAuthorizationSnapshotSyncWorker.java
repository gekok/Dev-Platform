package dev.platform.board.infrastructure.snapshot;

import java.time.OffsetDateTime;

import dev.platform.board.infrastructure.persistence.entity.ProjectAuthorizationSnapshotSyncJobEntity;
import dev.platform.board.infrastructure.persistence.repository.ProjectAuthorizationSnapshotSyncJobJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-platform.snapshot-sync.project.enabled", havingValue = "true")
public class ProjectAuthorizationSnapshotSyncWorker {

    private final ProjectAuthorizationSnapshotSyncJobJpaRepository jobs;
    private final RedisProjectAuthorizationSnapshotStore redisStore;

    @Scheduled(fixedDelayString = "${dev-platform.snapshot-sync.project.fixed-delay-ms:1000}")
    @Transactional
    public void syncDueSnapshots() {
        OffsetDateTime now = OffsetDateTime.now();
        for (ProjectAuthorizationSnapshotSyncJobEntity job : jobs
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc("PENDING", now)) {
            try {
                RedisProjectAuthorizationSnapshotStore.ApplyResult result = redisStore.apply(
                        job.getProjectId(), job.getUserId(), job.getSnapshotJson());
                OffsetDateTime appliedAt = OffsetDateTime.now();
                if (result == RedisProjectAuthorizationSnapshotStore.ApplyResult.SUPERSEDED) {
                    job.markSuperseded(appliedAt);
                } else {
                    job.markSynced(appliedAt);
                }
            } catch (RuntimeException exception) {
                job.scheduleRetry(OffsetDateTime.now(), exception.getMessage());
            }
        }
    }
}
