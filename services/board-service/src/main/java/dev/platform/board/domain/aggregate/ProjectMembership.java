package dev.platform.board.domain.aggregate;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectMembership(
        Long projectId,
        UUID userId,
        Role role,
        Status status,
        OffsetDateTime joinedAt,
        OffsetDateTime removedAt,
        OffsetDateTime updatedAt) {

    public enum Role {
        OWNER,
        MEMBER
    }

    public enum Status {
        ACTIVE,
        REMOVED
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public ProjectMembership remove(OffsetDateTime now) {
        if (!isActive()) {
            throw ProjectRuleViolation.notFound("Membership not found.");
        }
        return new ProjectMembership(projectId, userId, role, Status.REMOVED, joinedAt, now, now);
    }

    public ProjectMembership reactivateAsMember(OffsetDateTime now) {
        return new ProjectMembership(projectId, userId, Role.MEMBER, Status.ACTIVE, now, null, now);
    }

    public ProjectMembership withRole(Role newRole, OffsetDateTime now) {
        return new ProjectMembership(projectId, userId, newRole, status, joinedAt, removedAt, now);
    }
}
