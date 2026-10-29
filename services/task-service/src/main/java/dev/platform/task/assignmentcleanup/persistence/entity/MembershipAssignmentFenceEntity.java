package dev.platform.task.assignmentcleanup.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "membership_assignment_fences")
public class MembershipAssignmentFenceEntity {

    @EmbeddedId
    private MembershipAssignmentFenceId id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "fenced_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime fencedAt;

    public MembershipAssignmentFenceEntity(MembershipAssignmentFenceId id, UUID eventId, OffsetDateTime fencedAt) {
        this.id = id;
        this.eventId = eventId;
        this.fencedAt = fencedAt;
    }
}
