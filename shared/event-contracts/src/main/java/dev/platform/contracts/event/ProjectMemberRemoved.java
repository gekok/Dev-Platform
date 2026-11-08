package dev.platform.contracts.event;

import java.util.List;
import java.util.UUID;

public record ProjectMemberRemoved(
        UUID operationId,
        Long projectId,
        UUID userId,
        long membershipVersion,
        long authorizationVersion,
        List<Long> boardIds) {
}
