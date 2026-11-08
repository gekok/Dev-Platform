package dev.platform.board.application.port.out;

import java.util.UUID;

public interface MembershipSnapshotPort {
    void record(
            UUID operationId,
            Long projectId,
            UUID userId,
            String status,
            String role,
            long membershipVersion,
            long authorizationVersion);
}
