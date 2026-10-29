package dev.platform.board.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_authorization_snapshot_sync_jobs", indexes = {
        @Index(name = "idx_project_snapshot_sync_due", columnList = "status, next_attempt_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_project_member_snapshot_authorization_version", columnNames = {
                "project_id", "user_id", "authorization_version"
        })
})
public class ProjectAuthorizationSnapshotSyncJobEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "operation_id", nullable = false, unique = true)
    private UUID operationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "membership_version", nullable = false)
    private long membershipVersion;

    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;

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

    public ProjectAuthorizationSnapshotSyncJobEntity(
            UUID jobId,
            UUID operationId,
            Long projectId,
            UUID userId,
            long membershipVersion,
            long authorizationVersion,
            String snapshotJson,
            OffsetDateTime now) {
        this.jobId = jobId;
        this.operationId = operationId;
        this.projectId = projectId;
        this.userId = userId;
        this.membershipVersion = membershipVersion;
        this.authorizationVersion = authorizationVersion;
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

    public void markSuperseded(OffsetDateTime now) {
        status = "SUPERSEDED";
        updatedAt = now;
        lastError = null;
    }

    public void scheduleRetry(OffsetDateTime now, String error) {
        attemptCount++;
        nextAttemptAt = now.plusSeconds(Math.min(300L, 1L << Math.min(attemptCount, 8)));
        updatedAt = now;
        lastError = error;
    }
}
