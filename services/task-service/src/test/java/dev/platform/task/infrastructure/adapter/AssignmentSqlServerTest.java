package dev.platform.task.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import dev.platform.task.application.command.AssignCardCommand;
import dev.platform.task.application.port.in.AssignCardPort;
import dev.platform.task.application.exception.ProjectAccessDeniedException;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceEntity;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId;
import dev.platform.task.assignmentcleanup.persistence.repository.MembershipAssignmentFenceJpaRepository;
import dev.platform.task.infrastructure.persistence.entity.CardEntity;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import dev.platform.task.infrastructure.persistence.repository.TaskAssigneeJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@EnabledIfSystemProperty(named = "task.sqlserver.integration", matches = "true")
@Transactional
class AssignmentSqlServerTest {
    @Autowired CardJpaRepository cards;
    @Autowired TaskAssigneeJpaRepository assignees;
    @Autowired MembershipAssignmentFenceJpaRepository fences;
    @Autowired AssignCardPort assign;

    @Test
    void assignAndRejoinPersistsNewGeneration() {
        var user = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var card = cards.saveAndFlush(CardEntity.builder().projectId(987654L).boardId(987654L)
                .columnId(987654L).title("Assignment integration").build());
        assign.assign(new AssignCardCommand(987654L, card.getId(), user, 1L, actor));
        assign.assign(new AssignCardCommand(987654L, card.getId(), user, 2L, actor));
        assignees.flush();
        var stored = assignees.findFirstByIdTaskIdAndPrimaryTrue(card.getId()).orElseThrow();
        assertEquals(user, stored.getId().getUserId());
        assertEquals(2L, stored.getMembershipVersion());
        assertEquals(actor, stored.getAssignedByUserId());
        assertTrue(assignees
                .findTop100ByIdUserIdAndMembershipVersionAndCardProjectIdAndCardBoardIdInAndCardIdGreaterThanOrderByCardIdAsc(
                        user, 1L, 987654L, List.of(987654L), 0L).isEmpty());
        assertEquals(1, assignees
                .findTop100ByIdUserIdAndMembershipVersionAndCardProjectIdAndCardBoardIdInAndCardIdGreaterThanOrderByCardIdAsc(
                        user, 2L, 987654L, List.of(987654L), 0L).size());
    }

    @Test
    void revokedGenerationFenceRejectsAssignment() {
        var user = UUID.randomUUID();
        var card = cards.saveAndFlush(CardEntity.builder().projectId(987654L).boardId(987654L)
                .columnId(987654L).title("Fence integration").build());
        fences.saveAndFlush(new MembershipAssignmentFenceEntity(
                new MembershipAssignmentFenceId(987654L, user, 1L), UUID.randomUUID(), OffsetDateTime.now()));
        assertThrows(ProjectAccessDeniedException.class, () -> assign.assign(
                new AssignCardCommand(987654L, card.getId(), user, 1L, UUID.randomUUID())));
    }
}
