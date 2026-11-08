package dev.platform.board.domain.aggregate;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Invitation(
        Long id,
        Long projectId,
        UUID userId,
        UUID invitedByUserId,
        Status status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime respondedAt) {

    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELLED
    }

    public Invitation respond(Status response, OffsetDateTime now) {
        if (status != Status.PENDING) {
            throw ProjectRuleViolation.conflict("INVITATION_NOT_PENDING",
                    "Invitation is no longer pending.");
        }
        if (response != Status.ACCEPTED && response != Status.REJECTED && response != Status.CANCELLED) {
            throw ProjectRuleViolation.validation("Invalid invitation response.");
        }
        OffsetDateTime responseAt = response == Status.CANCELLED ? respondedAt : now;
        return new Invitation(id, projectId, userId, invitedByUserId, response, createdAt, now, responseAt);
    }
}
