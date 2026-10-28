package dev.platform.contracts.event;

import java.util.UUID;

/** Provisional completion payload. */
public record ProjectTaskDataDeleted(UUID operationId, UUID projectDeletionId, Long projectId) {
}
