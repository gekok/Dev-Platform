package dev.platform.contracts.event;

import java.util.List;
import java.util.UUID;

/** Provisional payload; boardIds are the task/notification cleanup scope. */
public record ProjectDeletionRequested(
        UUID operationId,
        UUID projectDeletionId,
        Long projectId,
        List<Long> boardIds,
        String reason) {
}
