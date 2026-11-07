package dev.platform.task.assignmentcleanup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${dev-platform.assignment-cleanup.fixed-delay-ms:1000}")
    public void processNextBatch() {
        AtomicReference<UUID> jobId = new AtomicReference<>();
        TransactionTemplate batch = new TransactionTemplate(transactionManager);
        batch.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            batch.executeWithoutResult(status -> jobs
                    .findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                            MemberAssignmentCleanupStatus.PENDING, OffsetDateTime.now())
                    .ifPresent(job -> {
                        jobId.set(job.getJobId());
                        process(job);
                    }));
        } catch (RuntimeException exception) {
            if (jobId.get() == null) throw exception;
            TransactionTemplate retry = new TransactionTemplate(transactionManager);
            retry.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            retry.executeWithoutResult(status -> jobs.lockByJobId(jobId.get())
                    .filter(job -> job.getStatus() == MemberAssignmentCleanupStatus.PENDING)
                    .ifPresent(job -> job.scheduleRetry(OffsetDateTime.now(), exception.getMessage())));
        }
    }

    private void process(MemberAssignmentCleanupJobEntity job) {
        membershipLock.lock(job.getProjectId(), job.getUserId());
        try {
            List<Long> boardIds = objectMapper.readValue(job.getBoardIdsJson(), new TypeReference<>() {
            });
            var fenceId = new dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId(
                    job.getProjectId(), job.getUserId(), job.getMembershipVersion());
            if (!fences.existsById(fenceId)) {
                throw new IllegalStateException("Assignment cleanup fence is missing for job " + job.getJobId());
            }
            if (boardIds.isEmpty()) {
                complete(job);
                return;
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
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
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
