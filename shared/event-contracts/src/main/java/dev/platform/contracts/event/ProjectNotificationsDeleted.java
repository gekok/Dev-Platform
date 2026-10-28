package dev.platform.contracts.event;

import java.util.UUID;

/** Provisional completion payload. */
public record ProjectNotificationsDeleted(UUID operationId, UUID projectDeletionId, Long projectId) {
}
