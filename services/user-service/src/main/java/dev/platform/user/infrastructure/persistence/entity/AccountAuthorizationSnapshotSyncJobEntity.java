package dev.platform.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "account_authorization_snapshot_sync_jobs",
    indexes = {
      @Index(name = "idx_account_snapshot_sync_due", columnList = "status, next_attempt_at")
    })
public class AccountAuthorizationSnapshotSyncJobEntity {

  @Id
  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "auth_version", nullable = false)
  private long authVersion;

  @Column(name = "snapshot_json", nullable = false, columnDefinition = "nvarchar(max)")
  private String snapshotJson;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", columnDefinition = "nvarchar(max)")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  public AccountAuthorizationSnapshotSyncJobEntity(
      UUID jobId, UUID accountId, long authVersion, String snapshotJson, OffsetDateTime now) {
    this.jobId = jobId;
    this.accountId = accountId;
    this.authVersion = authVersion;
    this.snapshotJson = snapshotJson;
    this.status = "PENDING";
    this.attemptCount = 0;
    this.createdAt = now;
    this.updatedAt = now;
    this.nextAttemptAt = now;
  }

  public void markSynced(OffsetDateTime now) {
    this.status = "SYNCED";
    this.updatedAt = now;
    this.lastError = null;
  }

  public void scheduleRetry(OffsetDateTime now, String error) {
    this.attemptCount++;
    long delaySeconds = Math.min(300L, 1L << Math.min(this.attemptCount, 8));
    this.nextAttemptAt = now.plusSeconds(delaySeconds);
    this.updatedAt = now;
    this.lastError = error;
  }
}
