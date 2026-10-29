package dev.platform.user.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_authorization_snapshot_sync_jobs", indexes = {
        @Index(name = "idx_account_snapshot_sync_due", columnList = "status, next_attempt_at")
}, uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uq_account_snapshot_version", columnNames = {
                "account_id", "auth_version"
        })
})
public class AccountAuthorizationSnapshotSyncJobEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    private UUID accountId;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime nextAttemptAt;

    public AccountAuthorizationSnapshotSyncJobEntity(
            UUID jobId, UUID accountId, long authVersion, String snapshotJson, OffsetDateTime now) {
        this.jobId = jobId;
        this.accountId = accountId;
        this.authVersion = authVersion;
        this.snapshotJson = snapshotJson;
        this.status = "PENDING";
        this.createdAt = now;
        this.updatedAt = now;
        this.nextAttemptAt = now;
    }

    public void markSynced(OffsetDateTime now) {
        status = "SYNCED";
        updatedAt = now;
        lastError = null;
    }

    public void scheduleRetry(OffsetDateTime now, String error) {
        attemptCount++;
        long delaySeconds = Math.min(300L, 1L << Math.min(attemptCount, 8));
        nextAttemptAt = now.plusSeconds(delaySeconds);
        updatedAt = now;
        lastError = error;
    }
}
