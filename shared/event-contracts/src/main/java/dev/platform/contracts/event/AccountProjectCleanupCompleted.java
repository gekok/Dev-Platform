package dev.platform.contracts.event;

import java.util.UUID;

/** Provisional completion payload. */
public record AccountProjectCleanupCompleted(UUID operationId, UUID accountId) {
}
