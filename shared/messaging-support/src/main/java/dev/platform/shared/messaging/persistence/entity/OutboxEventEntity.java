package dev.platform.shared.messaging.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_due", columnList = "status, next_attempt_at, created_at")
})
public class OutboxEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public static OutboxEventEntity pending(
            UUID eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            long aggregateVersion,
            String payload,
            OffsetDateTime now) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.eventId = eventId;
        entity.eventType = eventType;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.aggregateVersion = aggregateVersion;
        entity.payload = payload;
        entity.status = OutboxStatus.PENDING;
        entity.attemptCount = 0;
        entity.createdAt = now;
        entity.nextAttemptAt = now;
        return entity;
    }

    public void markSent(OffsetDateTime now) {
        status = OutboxStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    public void scheduleRetry(OffsetDateTime retryAt, String error) {
        status = OutboxStatus.PENDING;
        attemptCount++;
        nextAttemptAt = retryAt;
        lastError = error;
    }
}
