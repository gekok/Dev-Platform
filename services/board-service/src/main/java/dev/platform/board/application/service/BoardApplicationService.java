package dev.platform.board.application.service;

import dev.platform.board.application.command.*;
import dev.platform.board.application.port.in.BoardUseCasePort;
import dev.platform.board.application.port.out.BoardDataPort;
import dev.platform.board.application.port.out.ProjectDataPort;
import dev.platform.board.application.port.out.TaskCountPort;
import dev.platform.board.application.query.*;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardApplicationService implements BoardUseCasePort {
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> ALLOWED_SORTS = List.of(
            "createdAt,desc", "createdAt,asc", "name,asc", "name,desc");

    private final BoardDataPort boards;
    private final ProjectDataPort projects;
    private final TaskCountPort taskCounts;
    private final BoardColumnDeletionService columnDeletion;

    @Override
    @Transactional
    public Board create(CreateProjectBoardCommand command) {
        requireActor(command.actorId());
        Project project = requireProjectForUpdate(command.projectId());
        requireOwner(project.id(), command.actorId());
        project.requireActive();
        String key = Board.validateKey(command.boardKey());
        if (boards.existsInProject(project.id(), key)) {
            throw ProjectRuleViolation.conflict(
                    "BOARD_KEY_ALREADY_EXISTS", "boardKey is already in use in this Project.");
        }
        Board board = Board.create(project.id(), command.name(), key,
                command.actorId(), command.createDefaultColumns(), now());
        return boards.save(board);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardPage<BoardSummary> list(BoardsQuery query) {
        requireActor(query.actorId());
        Project project = requireProject(query.projectId());
        requireActiveMembership(project.id(), query.actorId());
        validatePage(query.page(), query.size());
        String status = validateBoardStatus(query.status());
        String sort = validateSort(query.sort());
        return boards.list(project.id(), status, query.page(), query.size(), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardView get(BoardQuery query) {
        requireActor(query.actorId());
        Board board = requireBoard(query.boardId());
        Project project = requireProject(board.projectId());
        ProjectMembership membership = requireActiveMembership(project.id(), query.actorId());
        return new BoardView(board, project.status() == Project.Status.ACTIVE
                && board.status() == Board.Status.ACTIVE, membership.role().name());
    }

    @Override
    @Transactional
    public Board rename(BoardNameCommand command) {
        Board board = requireBoardForUpdate(command.boardId());
        authorizeOwner(board, command.actorId());
        requireWritableProject(board.projectId());
        board.requireActive();
        if (command.boardKey() != null && !command.boardKey().equals(board.key())) {
            throw ProjectRuleViolation.validation("boardKey cannot be changed after Board creation.");
        }
        return boards.save(board.rename(command.name(), now()));
    }

    @Override
    @Transactional
    public Board changeStatus(BoardStatusCommand command) {
        Board board = requireBoardForUpdate(command.boardId());
        authorizeOwner(board, command.actorId());
        requireWritableProject(board.projectId());
        return boards.save(board.changeStatus(command.status(), now()));
    }

    @Override
    @Transactional
    public Board addColumn(AddBoardColumnCommand command) {
        Board board = requireBoardForUpdate(command.boardId());
        authorizeOwner(board, command.actorId());
        requireWritableProject(board.projectId());
        board.requireActive();
        Board changed = board.addColumn(command.name(), command.position(), now());
        boards.save(changed);
        boards.saveColumnsInOrder(board.id(), changed.columns());
        return requireBoard(board.id());
    }

    @Override
    @Transactional
    public Board renameColumn(RenameBoardColumnCommand command) {
        Board.Column column = boards.findColumn(command.columnId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Column not found."));
        Board board = requireBoardForUpdate(column.boardId());
        authorizeOwner(board, command.actorId());
        requireWritableProject(board.projectId());
        board.requireActive();
        Board changed = board.renameColumn(column.id(), command.name(), now());
        boards.save(changed);
        boards.renameColumn(board.id(), column.id(),
                Board.validateColumnName(command.name()));
        return requireBoard(board.id());
    }

    @Override
    @Transactional
    public Board reorderColumns(ReorderBoardColumnsCommand command) {
        Board board = requireBoardForUpdate(command.boardId());
        authorizeOwner(board, command.actorId());
        requireWritableProject(board.projectId());
        board.requireActive();
        Board changed = board.reorder(command.columnIds(), now());
        boards.save(changed);
        boards.saveColumnsInOrder(board.id(), changed.columns());
        return requireBoard(board.id());
    }

    @Override
    public void deleteColumn(DeleteBoardColumnCommand command) {
        requireActor(command.actorId());
        Board.Column column = boards.findColumn(command.columnId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Column not found."));
        Board board = requireBoard(column.boardId());
        authorizeOwner(board, command.actorId());
        requireWritableProject(board.projectId());
        board.requireActive();
        long count = taskCounts.countByColumnId(column.id());
        if (count > 0) {
            throw ProjectRuleViolation.conflict("COLUMN_NOT_EMPTY",
                    "Column contains Tasks and cannot be deleted.");
        }
        columnDeletion.delete(command);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardAccess verifyBoard(InternalBoardQuery query) {
        Board board = requireBoard(query.boardId());
        Project project = requireProject(board.projectId());
        return new BoardAccess(board.id(), board.projectId(), board.status().name(),
                project.status().name(), board.columns());
    }

    private Board requireBoard(Long boardId) {
        return boards.find(boardId).orElseThrow(() -> ProjectRuleViolation.notFound("Board not found."));
    }

    private Board requireBoardForUpdate(Long boardId) {
        Board candidate = requireBoard(boardId);
        projects.findProjectForUpdate(candidate.projectId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
        return boards.findForUpdate(boardId).orElseThrow(() -> ProjectRuleViolation.notFound("Board not found."));
    }

    private Project requireProject(Long projectId) {
        return projects.findProject(projectId)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
    }

    private Project requireProjectForUpdate(Long projectId) {
        return projects.findProjectForUpdate(projectId)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
    }

    private void authorizeOwner(Board board, UUID actorId) {
        requireActor(actorId);
        requireOwner(board.projectId(), actorId);
    }

    private void requireWritableProject(Long projectId) {
        requireProject(projectId).requireActive();
    }

    private ProjectMembership requireActiveMembership(Long projectId, UUID actorId) {
        return projects.findMembership(projectId, actorId)
                .filter(ProjectMembership::isActive)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Board not found."));
    }

    private ProjectMembership requireOwner(Long projectId, UUID actorId) {
        ProjectMembership membership = requireActiveMembership(projectId, actorId);
        if (membership.role() != ProjectMembership.Role.OWNER) {
            throw ProjectRuleViolation.forbidden("Only the Project Owner can change Board structure.");
        }
        return membership;
    }

    private String validateBoardStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Board.Status.valueOf(status.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw ProjectRuleViolation.validation("status must be ACTIVE, COMPLETED, or CANCELLED.");
        }
    }

    private String validateSort(String sort) {
        String value = sort == null || sort.isBlank() ? "createdAt,desc" : sort;
        if (!ALLOWED_SORTS.contains(value)) {
            throw ProjectRuleViolation.validation("sort must be createdAt,desc; createdAt,asc; name,asc; or name,desc.");
        }
        return value;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ProjectRuleViolation.validation("page must be non-negative and size must be between 1 and 100.");
        }
    }

    private void requireActor(UUID actorId) {
        if (actorId == null) {
            throw ProjectRuleViolation.unauthorized("Authenticated user identity is required.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
