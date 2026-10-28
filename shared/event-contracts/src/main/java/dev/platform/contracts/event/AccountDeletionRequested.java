package dev.platform.contracts.event;

import java.util.UUID;

/** Provisional payload; finalize fields with the event contract before enabling consumers. */
public record AccountDeletionRequested(UUID operationId, UUID accountId, String reason) {
}
