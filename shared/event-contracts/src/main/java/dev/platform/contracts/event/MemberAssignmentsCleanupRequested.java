package dev.platform.contracts.event;

import java.util.List;
import java.util.UUID;

/** Provisional payload for clearing assignments when a user leaves a Project. */
public record MemberAssignmentsCleanupRequested(
        UUID operationId,
        Long projectId,
        UUID userId,
        List<Long> boardIds,
        long membershipVersion) {
}
