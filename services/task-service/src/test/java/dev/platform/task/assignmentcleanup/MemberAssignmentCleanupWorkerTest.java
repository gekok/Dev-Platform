package dev.platform.task.assignmentcleanup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.task.assignmentcleanup.persistence.entity.MemberAssignmentCleanupJobEntity;
import dev.platform.task.assignmentcleanup.persistence.repository.MemberAssignmentCleanupJobJpaRepository;
import dev.platform.task.assignmentcleanup.persistence.repository.MembershipAssignmentFenceJpaRepository;
import dev.platform.task.infrastructure.persistence.repository.TaskAssigneeJpaRepository;
import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeEntity;
import dev.platform.task.infrastructure.persistence.entity.CardEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class MemberAssignmentCleanupWorkerTest {
    @Test
    void missingFenceDoesNotCompleteAnEmptyBoardScope() {
        var jobs = mock(MemberAssignmentCleanupJobJpaRepository.class);
        var fences = mock(MembershipAssignmentFenceJpaRepository.class);
        var outbox = mock(OutboxEventWriter.class);
        var job = mock(MemberAssignmentCleanupJobEntity.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var jobId = UUID.randomUUID();
        when(job.getJobId()).thenReturn(jobId);
        when(job.getStatus()).thenReturn(MemberAssignmentCleanupStatus.PENDING);
        when(jobs.lockByJobId(jobId)).thenReturn(Optional.of(job));
        when(jobs.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(Optional.of(job));
        when(job.getBoardIdsJson()).thenReturn("[]");
        var worker = new MemberAssignmentCleanupWorker(jobs, mock(TaskAssigneeJpaRepository.class), fences,
                mock(MembershipAssignmentLock.class), outbox, new ObjectMapper(), manager);
        worker.processNextBatch();
        verify(outbox, never()).append(any());
        verify(job, never()).markBatchProcessed(any(), anyBoolean(), any());
        var order = inOrder(manager, job);
        order.verify(manager).rollback(any());
        order.verify(job).scheduleRetry(any(), any());
    }

    @Test
    void outboxFailureRollsBackBatchBeforeSchedulingRetry() {
        var jobs = mock(MemberAssignmentCleanupJobJpaRepository.class);
        var assignees = mock(TaskAssigneeJpaRepository.class);
        var fences = mock(MembershipAssignmentFenceJpaRepository.class);
        var outbox = mock(OutboxEventWriter.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var job = mock(MemberAssignmentCleanupJobEntity.class);
        var jobId = UUID.randomUUID();
        when(job.getJobId()).thenReturn(jobId);
        when(job.getStatus()).thenReturn(MemberAssignmentCleanupStatus.PENDING);
        when(job.getBoardIdsJson()).thenReturn("[1]");
        when(job.getProjectId()).thenReturn(1L);
        when(job.getUserId()).thenReturn(UUID.randomUUID());
        when(job.getMembershipVersion()).thenReturn(1L);
        when(job.getLastProcessedCardId()).thenReturn(0L);
        when(jobs.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(any(), any()))
                .thenReturn(Optional.of(job));
        when(jobs.lockByJobId(jobId)).thenReturn(Optional.of(job));
        when(fences.existsById(any())).thenReturn(true);
        var assignment = mock(TaskAssigneeEntity.class);
        var card = new CardEntity();
        card.setId(42L);
        when(assignment.getCard()).thenReturn(card);
        when(assignees.findTop100ByIdUserIdAndMembershipVersionAndCardProjectIdAndCardBoardIdInAndCardIdGreaterThanOrderByCardIdAsc(
                any(), anyLong(), anyLong(), anyCollection(), anyLong())).thenReturn(List.of(assignment));
        doThrow(new IllegalStateException("outbox unavailable")).when(outbox).append(any());
        var worker = new MemberAssignmentCleanupWorker(jobs, assignees, fences,
                mock(MembershipAssignmentLock.class), outbox, new ObjectMapper(), manager);

        worker.processNextBatch();

        var order = inOrder(assignees, manager, job);
        order.verify(assignees).deleteAllInBatch(any());
        order.verify(manager).rollback(any());
        order.verify(job).scheduleRetry(any(), eq("outbox unavailable"));
    }
}
