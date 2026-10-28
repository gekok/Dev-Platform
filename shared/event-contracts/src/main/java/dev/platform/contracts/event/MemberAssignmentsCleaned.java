package dev.platform.contracts.event;

import java.util.UUID;

/** Provisional completion payload. */
public record MemberAssignmentsCleaned(UUID operationId, Long projectId, UUID userId) {
}
