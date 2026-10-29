package dev.platform.board.infrastructure.snapshot;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.MemberAssignmentsCleanupRequested;
import dev.platform.contracts.event.MembershipChanged;
import dev.platform.contracts.event.MembershipRemoved;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.board.infrastructure.persistence.entity.ProjectAuthorizationSnapshotSyncJobEntity;
import dev.platform.board.infrastructure.persistence.repository.ProjectAuthorizationSnapshotSyncJobJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProjectAuthorizationSnapshotSyncRecorder {

    private final ProjectAuthorizationSnapshotSyncJobJpaRepository jobs;
    private final OutboxEventWriter outbox;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordMembershipChanged(
            UUID operationId, Long projectId, UUID userId, String status, String role,
            long membershipVersion, long authorizationVersion) {
        OffsetDateTime now = OffsetDateTime.now();
        ProjectMemberAuthorizationSnapshot snapshot = new ProjectMemberAuthorizationSnapshot(
                projectId, userId, status, role, membershipVersion, authorizationVersion);
        saveSnapshotJob(operationId, snapshot, now);
        append(EventTypes.MEMBERSHIP_CHANGED, projectId, userId, authorizationVersion, now,
                new MembershipChanged(projectId, userId, status, role, membershipVersion));
    }

    @Transactional
    public void recordMembershipRemoved(
            UUID operationId,
            Long projectId,
            UUID userId,
            long membershipVersion,
            long authorizationVersion,
            List<Long> boardIds) {
        OffsetDateTime now = OffsetDateTime.now();
        ProjectMemberAuthorizationSnapshot snapshot = new ProjectMemberAuthorizationSnapshot(
                projectId, userId, "REMOVED", null, membershipVersion, authorizationVersion);
        saveSnapshotJob(operationId, snapshot, now);
        append(EventTypes.MEMBERSHIP_REMOVED, projectId, userId, authorizationVersion, now,
                new MembershipRemoved(projectId, userId, membershipVersion));
        append(EventTypes.MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED, projectId, userId, authorizationVersion, now,
                new MemberAssignmentsCleanupRequested(
                        operationId, projectId, userId, List.copyOf(boardIds), membershipVersion));
    }

    private void saveSnapshotJob(
            UUID operationId, ProjectMemberAuthorizationSnapshot snapshot, OffsetDateTime now) {
        try {
            jobs.save(new ProjectAuthorizationSnapshotSyncJobEntity(
                    UUID.randomUUID(), operationId, snapshot.projectId(), snapshot.userId(), snapshot.membershipVersion(),
                    snapshot.authorizationVersion(), objectMapper.writeValueAsString(snapshot), now));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Could not serialize Project membership snapshot " + snapshot.projectId() + ":" + snapshot.userId(),
                    exception);
        }
    }

    private void append(String eventType, Long projectId, UUID userId, long version,
            OffsetDateTime now, Object payload) {
        String aggregateId = projectId + ":" + userId;
        outbox.append(new EventEnvelope<>(UUID.randomUUID(), eventType, 1,
                "PROJECT_MEMBERSHIP", aggregateId, version, now, payload));
    }
}
