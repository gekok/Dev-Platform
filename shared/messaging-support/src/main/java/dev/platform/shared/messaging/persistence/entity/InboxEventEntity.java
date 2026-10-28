package dev.platform.shared.messaging.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity
@IdClass(InboxEventId.class)
@Table(name = "inbox_events")
public class InboxEventEntity {

    @Id
    @Column(name = "consumer_name", nullable = false, length = 120)
    private String consumerName;

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "received_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime receivedAt;

    public InboxEventEntity(String consumerName, UUID eventId, String eventType, OffsetDateTime receivedAt) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.eventType = eventType;
        this.receivedAt = receivedAt;
    }
}
