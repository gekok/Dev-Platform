package dev.platform.user.application.deletion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountDeletionOperationView(
        UUID operationId,
        UUID accountId,
        AccountDeletionOperationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
