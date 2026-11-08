package dev.platform.board.application.service;

import dev.platform.board.application.command.DeleteBoardColumnCommand;
import dev.platform.board.application.port.out.BoardDataPort;
import dev.platform.board.application.port.out.ProjectDataPort;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class BoardColumnDeletionService {
    private final BoardDataPort data;
    private final ProjectDataPort projects;

    @Transactional
    public void delete(DeleteBoardColumnCommand command) {
        requireActor(command.actorId());
        var column = data.findColumn(command.columnId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Column not found."));
        Board candidate = data.find(column.boardId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Board not found."));
        Project project = projects.findProjectForUpdate(candidate.projectId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
        Board board = data.findForUpdate(column.boardId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Board not found."));
        ProjectMembership membership = projects.findMembership(board.projectId(), command.actorId())
                .filter(ProjectMembership::isActive)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Board not found."));
        if (membership.role() != ProjectMembership.Role.OWNER) {
            throw ProjectRuleViolation.forbidden("Only the Project Owner can change Board structure.");
        }
        project.requireActive();
        board.requireActive();
        Board changed = board.removeColumn(command.columnId(), OffsetDateTime.now(ZoneOffset.UTC));
        data.save(changed);
        data.deleteColumnAndReindex(board.id(), command.columnId(), changed.columns());
    }

    private void requireActor(java.util.UUID actorId) {
        if (actorId == null) {
            throw ProjectRuleViolation.unauthorized("Authenticated user identity is required.");
        }
    }
}
