package dev.platform.board.infrastructure.snapshot;

import java.util.UUID;

public record ProjectMemberAuthorizationSnapshot(
        Long projectId,
        UUID userId,
        String status,
        String role,
        long membershipVersion,
        long authorizationVersion) {
}
