package dev.platform.board.application.port.out;

import java.util.List;
import java.util.UUID;

public interface ProjectEventPort {
    void projectStatusChanged(Long projectId, String previousStatus, String status);

    void memberAdded(Long projectId, UUID userId, long membershipVersion, long authorizationVersion);

    void memberRemoved(
            UUID operationId,
            Long projectId,
            UUID userId,
            long membershipVersion,
            long authorizationVersion,
            List<Long> boardIds);

    void ownerTransferred(Long projectId, UUID fromUserId, UUID toUserId, long aggregateVersion);
}
