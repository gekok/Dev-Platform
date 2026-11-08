package dev.platform.board.domain.aggregate;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Project(
        Long id,
        String name,
        Status status,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public enum Status {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    public static String validateName(String name) {
        if (name == null) {
            throw ProjectRuleViolation.validation("name is required");
        }
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw ProjectRuleViolation.validation("name must contain 1 to 255 characters");
        }
        return normalized;
    }

    public Project rename(String newName, OffsetDateTime now) {
        requireActive();
        String normalized = validateName(newName);
        return normalized.equals(name) ? this
                : new Project(id, normalized, status, createdByUserId, createdAt, now);
    }

    public Project changeStatus(Status newStatus, OffsetDateTime now) {
        if (newStatus == null) {
            throw ProjectRuleViolation.validation("status is required");
        }
        if (status == newStatus) {
            return this;
        }
        if (status != Status.ACTIVE && newStatus != Status.ACTIVE) {
            throw ProjectRuleViolation.conflict("INVALID_STATUS_TRANSITION",
                    "A completed or cancelled Project must be reopened before changing status.");
        }
        return new Project(id, name, newStatus, createdByUserId, createdAt, now);
    }

    public void requireActive() {
        if (status != Status.ACTIVE) {
            throw ProjectRuleViolation.conflict("PROJECT_NOT_ACTIVE", "Project is not active.");
        }
    }
}
