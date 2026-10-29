package dev.platform.task.assignmentcleanup;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.event.MemberAssignmentsCleanupRequested;
import dev.platform.shared.messaging.InboxEventRecorder;
import dev.platform.task.assignmentcleanup.persistence.entity.MemberAssignmentCleanupJobEntity;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceEntity;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId;
import dev.platform.task.assignmentcleanup.persistence.repository.MemberAssignmentCleanupJobJpaRepository;
import dev.platform.task.assignmentcleanup.persistence.repository.MembershipAssignmentFenceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberAssignmentCleanupRecorder implements MemberAssignmentCleanupHandler {

    private static final String CONSUMER_NAME = "task-service.member-assignment-cleanup";

    private final InboxEventRecorder inbox;
    private final MembershipAssignmentLock membershipLock;
    private final MembershipAssignmentFenceJpaRepository fences;
    private final MemberAssignmentCleanupJobJpaRepository jobs;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void requestMemberAssignmentCleanup(EventEnvelope<MemberAssignmentsCleanupRequested> event) {
        if (!inbox.recordIfNew(CONSUMER_NAME, event.eventId(), event.eventType())) {
            return;
        }
        MemberAssignmentsCleanupRequested request = event.payload();
        membershipLock.lock(request.projectId(), request.userId());
        String key = deduplicationKey(request.projectId(), request.userId(), request.membershipVersion());
        if (jobs.findByDeduplicationKey(key).isPresent()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        MembershipAssignmentFenceId fenceId = new MembershipAssignmentFenceId(
                request.projectId(), request.userId(), request.membershipVersion());
        fences.findById(fenceId).orElseGet(() -> fences.save(new MembershipAssignmentFenceEntity(
                fenceId, event.eventId(), now)));
        try {
            jobs.save(new MemberAssignmentCleanupJobEntity(
                    UUID.randomUUID(), key, event.eventId(), request.operationId(), request.projectId(),
                    request.userId(), request.membershipVersion(), event.aggregateVersion(),
                    objectMapper.writeValueAsString(request.boardIds()), now));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize assignment cleanup board scope", exception);
        }
    }

    public static String deduplicationKey(Long projectId, UUID userId, long membershipVersion) {
        return projectId + ":" + userId + ":" + membershipVersion;
    }
}
