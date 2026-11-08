package dev.platform.board.infrastructure.adapter;

import dev.platform.board.application.port.out.ProjectEventPort;
import dev.platform.board.infrastructure.snapshot.ProjectAuthorizationSnapshotSyncRecorder;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.MemberAdded;
import dev.platform.contracts.event.OwnerTransferred;
import dev.platform.contracts.event.ProjectMemberRemoved;
import dev.platform.contracts.event.ProjectStatusChanged;
import dev.platform.shared.messaging.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectEventAdapter implements ProjectEventPort {
    private final OutboxEventWriter outbox;
    private final ProjectAuthorizationSnapshotSyncRecorder snapshots;

    @Override
    @Transactional
    public void projectStatusChanged(Long projectId, String previousStatus, String status) {
        OffsetDateTime now = OffsetDateTime.now();
        append(EventTypes.PROJECT_STATUS_CHANGED, "PROJECT", projectId.toString(),
                now.toInstant().toEpochMilli(), now,
                new ProjectStatusChanged(projectId, previousStatus, status));
    }

    @Override
    @Transactional
    public void memberAdded(Long projectId, UUID userId, long membershipVersion, long authorizationVersion) {
        OffsetDateTime now = OffsetDateTime.now();
        append(EventTypes.MEMBER_ADDED, "PROJECT_MEMBERSHIP", projectId + ":" + userId,
                authorizationVersion, now, new MemberAdded(projectId, userId, membershipVersion));
    }

    @Override
    @Transactional
    public void memberRemoved(
            UUID operationId,
            Long projectId,
            UUID userId,
            long revokedMembershipVersion,
            long membershipVersion,
            long authorizationVersion,
            List<Long> boardIds) {
        OffsetDateTime now = OffsetDateTime.now();
        snapshots.recordMembershipRemoved(operationId, projectId, userId ,revokedMembershipVersion,
                membershipVersion, authorizationVersion, boardIds);
        append(EventTypes.MEMBER_REMOVED, "PROJECT_MEMBERSHIP", projectId + ":" + userId,
                authorizationVersion, now,
                new ProjectMemberRemoved(operationId, projectId, userId,
                        membershipVersion, authorizationVersion, List.copyOf(boardIds)));
    }

    @Override
    @Transactional
    public void ownerTransferred(Long projectId, UUID fromUserId, UUID toUserId, long aggregateVersion) {
        OffsetDateTime now = OffsetDateTime.now();
        append(EventTypes.OWNER_TRANSFERRED, "PROJECT", projectId.toString(),
                aggregateVersion, now, new OwnerTransferred(projectId, fromUserId, toUserId));
    }

    private void append(
            String type, String aggregateType, String aggregateId, long version,
            OffsetDateTime now, Object payload) {
        outbox.append(new EventEnvelope<>(UUID.randomUUID(), type, 1, aggregateType,
                aggregateId, version, now, payload));
    }
}
