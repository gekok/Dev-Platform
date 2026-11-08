package dev.platform.board.application.query;

import java.util.UUID;

public record SnapshotSyncJobView(UUID operationId, Long projectId, UUID userId, String status) {}
