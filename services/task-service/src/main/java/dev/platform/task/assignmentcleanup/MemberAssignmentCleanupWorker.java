package dev.platform.task.assignmentcleanup;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.MemberAssignmentsCleaned;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.task.assignmentcleanup.persistence.entity.MemberAssignmentCleanupJobEntity;
import dev.platform.task.assignmentcleanup.persistence.repository.MemberAssignmentCleanupJobJpaRepository;
import dev.platform.task.assignmentcleanup.persistence.repository.MembershipAssignmentFenceJpaRepository;
import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeEntity;
import dev.platform.task.infrastructure.persistence.repository.TaskAssigneeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-platform.assignment-cleanup.enabled", havingValue = "true")
public class MemberAssignmentCleanupWorker {

    private final MemberAssignmentCleanupJobJpaRepository jobs;
    private final TaskAssigneeJpaRepository assignees;
    private final MembershipAssignmentFenceJpaRepository fences;
    private final MembershipAssignmentLock membershipLock;
    private final OutboxEventWriter outbox;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${dev-platform.assignment-cleanup.fixed-delay-ms:1000}")
    @Transactional
    public void processNextBatch() {
        jobs.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                MemberAssignmentCleanupStatus.PENDING, OffsetDateTime.now()).ifPresent(this::process);
    }

    private void process(MemberAssignmentCleanupJobEntity job) {
        membershipLock.lock(job.getProjectId(), job.getUserId());
        try {
            List<Long> boardIds = objectMapper.readValue(job.getBoardIdsJson(), new TypeReference<>() {
            });
            if (boardIds.isEmpty()) {
                complete(job);
                return;
            }
            var fenceId = new dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId(
                    job.getProjectId(), job.getUserId(), job.getMembershipVersion());
            if (!fences.existsById(fenceId)) {
                throw new IllegalStateException("Assignment cleanup fence is missing for job " + job.getJobId());
            }
            List<TaskAssigneeEntity> batch = assignees
                    .findTop100ByIdUserIdAndMembershipVersionAndCardProjectIdAndCardBoardIdInAndCardIdGreaterThanOrderByCardIdAsc(
                            job.getUserId(), job.getMembershipVersion(), job.getProjectId(), boardIds,
                            job.getLastProcessedCardId());
            if (batch.isEmpty()) {
                complete(job);
                return;
            }
            Long lastCardId = batch.getLast().getCard().getId();
            assignees.deleteAllInBatch(batch);
            boolean hasMore = batch.size() == 100;
            job.markBatchProcessed(lastCardId, hasMore, OffsetDateTime.now());
            if (!hasMore) {
                publishCompletion(job);
            }
        } catch (RuntimeException | IOException exception) {
            job.scheduleRetry(OffsetDateTime.now(), exception.getMessage());
        }
    }

    private void complete(MemberAssignmentCleanupJobEntity job) {
        job.markBatchProcessed(job.getLastProcessedCardId(), false, OffsetDateTime.now());
        publishCompletion(job);
    }

    private void publishCompletion(MemberAssignmentCleanupJobEntity job) {
        OffsetDateTime now = OffsetDateTime.now();
        MemberAssignmentsCleaned payload = new MemberAssignmentsCleaned(
                job.getOperationId(), job.getProjectId(), job.getUserId(), job.getMembershipVersion());
        outbox.append(new EventEnvelope<>(UUID.randomUUID(), EventTypes.MEMBER_ASSIGNMENTS_CLEANED,
                1, "PROJECT_MEMBERSHIP", job.getProjectId() + ":" + job.getUserId(),
                job.getSourceAggregateVersion(), now, payload));
    }
}
