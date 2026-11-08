package dev.platform.board.application.service;

import dev.platform.board.application.command.BoardNameCommand;
import dev.platform.board.application.port.out.BoardDataPort;
import dev.platform.board.application.port.out.ProjectDataPort;
import dev.platform.board.application.port.out.TaskCountPort;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BoardApplicationServiceTest {
    private final BoardDataPort boards = mock(BoardDataPort.class);
    private final ProjectDataPort projects = mock(ProjectDataPort.class);
    private final BoardApplicationService service = new BoardApplicationService(
            boards, projects, mock(TaskCountPort.class), mock(BoardColumnDeletionService.class));

    @Test
    void nonMemberCannotLearnBoardExists() {
        UUID actor = UUID.randomUUID();
        Board board = board();
        when(boards.find(3L)).thenReturn(Optional.of(board));
        when(projects.findProject(5L)).thenReturn(Optional.of(project()));
        when(projects.findMembership(5L, actor)).thenReturn(Optional.empty());

        ProjectRuleViolation violation = assertThrows(ProjectRuleViolation.class,
                () -> service.get(new dev.platform.board.application.query.BoardQuery(3L, actor)));

        assertEquals(404, violation.httpStatus());
    }

    @Test
    void projectMemberCannotRenameBoard() {
        UUID actor = UUID.randomUUID();
        when(boards.findForUpdate(3L)).thenReturn(Optional.of(board()));
        when(boards.find(3L)).thenReturn(Optional.of(board()));
        when(projects.findProjectForUpdate(5L)).thenReturn(Optional.of(project()));
        when(projects.findMembership(5L, actor)).thenReturn(Optional.of(
                new ProjectMembership(5L, actor, ProjectMembership.Role.MEMBER,
                        ProjectMembership.Status.ACTIVE, now(), null, now())));

        ProjectRuleViolation violation = assertThrows(ProjectRuleViolation.class,
                () -> service.rename(new BoardNameCommand(3L, "Renamed", null, actor)));

        assertEquals(403, violation.httpStatus());
        verify(boards, never()).save(any());
    }

    private Board board() {
        return new Board(3L, 5L, "Board", "board-1", Board.Status.ACTIVE,
                UUID.randomUUID(), now(), now(), java.util.List.of());
    }

    private Project project() {
        return new Project(5L, "Project", Project.Status.ACTIVE, UUID.randomUUID(), now(), now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.parse("2026-11-08T00:00:00Z");
    }
}
