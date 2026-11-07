package dev.platform.task.assignmentcleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.task.assignmentcleanup.persistence.entity.MemberAssignmentCleanupJobEntity;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceEntity;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId;
import dev.platform.task.assignmentcleanup.persistence.repository.MemberAssignmentCleanupJobJpaRepository;
import dev.platform.task.assignmentcleanup.persistence.repository.MembershipAssignmentFenceJpaRepository;
import dev.platform.task.infrastructure.persistence.entity.CardCommentEntity;
import dev.platform.task.infrastructure.persistence.entity.CardEntity;
import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeEntity;
import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeId;
import dev.platform.task.infrastructure.persistence.repository.CardCommentJpaRepository;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import dev.platform.task.infrastructure.persistence.repository.TaskAssigneeJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@EnabledIfSystemProperty(named = "task.sqlserver.integration", matches = "true")
class MemberAssignmentCleanupSqlServerTest {

    @Autowired MemberAssignmentCleanupJobJpaRepository jobs;
    @Autowired MembershipAssignmentFenceJpaRepository fences;
    @Autowired TaskAssigneeJpaRepository assignees;
    @Autowired CardCommentJpaRepository comments;
    @Autowired CardJpaRepository cards;
    @Autowired MembershipAssignmentLock membershipLock;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void outboxFailureRollsBackSqlDeleteBeforePersistingRetry() {
        long projectId = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1_000_000_000_000L) + 1;
        long boardId = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000_000L) + 1;
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        long membershipVersion = 1L;
        MembershipAssignmentFenceId fenceId = new MembershipAssignmentFenceId(projectId, userId, membershipVersion);
        CardEntity card = null;

        try {
            card = cards.saveAndFlush(CardEntity.builder()
                    .projectId(projectId)
                    .boardId(boardId)
                    .columnId(boardId + 1)
                    .title("Cleanup rollback integration")
                    .build());
            assignees.saveAndFlush(new TaskAssigneeEntity(
                    card, userId, actorId, OffsetDateTime.now(), membershipVersion));
            comments.saveAndFlush(CardCommentEntity.builder()
                    .card(card)
                    .authorId(actorId)
                    .content("Keep comment history")
                    .createdAt(OffsetDateTime.now())
                    .build());
            fences.saveAndFlush(new MembershipAssignmentFenceEntity(fenceId, eventId, OffsetDateTime.now()));
            jobs.saveAndFlush(new MemberAssignmentCleanupJobEntity(
                    jobId,
                    MemberAssignmentCleanupRecorder.deduplicationKey(projectId, userId, membershipVersion),
                    eventId,
                    operationId,
                    projectId,
                    userId,
                    membershipVersion,
                    4L,
                    "[" + boardId + "]",
                    OffsetDateTime.now().minusMinutes(1)));

            OutboxEventWriter failingOutbox = mock(OutboxEventWriter.class);
            doThrow(new IllegalStateException("outbox unavailable"))
                    .when(failingOutbox).append(any());
            MemberAssignmentCleanupWorker worker = new MemberAssignmentCleanupWorker(
                    jobs, assignees, fences, membershipLock, failingOutbox, objectMapper, transactionManager);

            worker.processNextBatch();

            TaskAssigneeEntity persistedAssignment = assignees
                    .findFirstByIdTaskIdAndPrimaryTrue(card.getId()).orElseThrow();
            assertEquals(membershipVersion, persistedAssignment.getMembershipVersion());
            assertEquals(1, comments.findByCard_ProjectIdAndCard_IdOrderByIdAsc(projectId, card.getId()).size());

            MemberAssignmentCleanupJobEntity retriedJob = jobs.findById(jobId).orElseThrow();
            assertEquals(MemberAssignmentCleanupStatus.PENDING, retriedJob.getStatus());
            assertEquals(1, retriedJob.getAttemptCount());
            assertEquals("outbox unavailable", retriedJob.getLastError());
            assertNotNull(retriedJob.getNextAttemptAt());
        } finally {
            if (card != null) {
                comments.deleteAll(comments.findByCard_ProjectIdAndCard_IdOrderByIdAsc(projectId, card.getId()));
                assignees.deleteById(new TaskAssigneeId(card.getId(), userId));
                cards.deleteById(card.getId());
            }
            jobs.deleteById(jobId);
            fences.deleteById(fenceId);
        }
    }
}
