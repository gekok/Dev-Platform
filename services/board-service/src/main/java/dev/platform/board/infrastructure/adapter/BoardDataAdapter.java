package dev.platform.board.infrastructure.adapter;

import dev.platform.board.application.port.out.BoardDataPort;
import dev.platform.board.application.query.BoardPage;
import dev.platform.board.application.query.BoardSummary;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.infrastructure.persistence.entity.BoardColumnEntity;
import dev.platform.board.infrastructure.persistence.entity.BoardEntity;
import dev.platform.board.infrastructure.persistence.entity.ProjectEntity;
import dev.platform.board.infrastructure.persistence.repository.BoardColumnJpaRepository;
import dev.platform.board.infrastructure.persistence.repository.BoardJpaRepository;
import dev.platform.board.infrastructure.persistence.repository.ProjectJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BoardDataAdapter implements BoardDataPort {
    private final BoardJpaRepository boards;
    private final BoardColumnJpaRepository columns;
    private final ProjectJpaRepository projects;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Board save(Board board) {
        boolean creating = board.id() == null;
        BoardEntity entity = creating
                ? new BoardEntity()
                : boards.findById(board.id()).orElseThrow(() ->
                        dev.platform.board.domain.aggregate.ProjectRuleViolation.notFound("Board not found."));
        entity.setName(board.name());
        entity.setBoardKey(board.key());
        entity.setStatus(board.status().name());
        entity.setCreatedByUserId(board.createdByUserId());
        entity.setCreatedAt(board.createdAt());
        entity.setUpdatedAt(board.updatedAt());
        if (creating) {
            ProjectEntity project = projects.getReferenceById(board.projectId());
            entity.setProjectEntity(project);
        }
        try {
            BoardEntity saved = boards.saveAndFlush(entity);
            if (creating && !board.columns().isEmpty()) {
                saveColumnsInOrder(saved.getId(), board.columns());
            }
            return find(saved.getId()).orElseThrow(() ->
                    dev.platform.board.domain.aggregate.ProjectRuleViolation.internal(
                            "BOARD_PERSISTENCE_FAILED", "Saved Board could not be reloaded."));
        } catch (DataIntegrityViolationException exception) {
            String details = exception.getMostSpecificCause().getMessage();
            if (details != null && details.toLowerCase(Locale.ROOT).contains("uq_boards_project_key")) {
                throw dev.platform.board.domain.aggregate.ProjectRuleViolation.conflict(
                        "BOARD_KEY_ALREADY_EXISTS", "boardKey is already in use in this Project.");
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Board> find(Long boardId) {
        return boards.findById(boardId).map(this::toBoard);
    }

    @Override
    @Transactional
    public Optional<Board> findForUpdate(Long boardId) {
        return boards.findForUpdate(boardId).map(this::toBoard);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardPage<BoardSummary> list(Long projectId, String status, int page, int size, String sort) {
        String[] parts = sort.split(",");
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);
        Sort ordering = Sort.by(direction, parts[0]).and(Sort.by(Sort.Direction.DESC, "id"));
        Page<BoardEntity> result = boards.findByProjectAndStatus(
                projectId, status, PageRequest.of(page, size, ordering));
        List<BoardSummary> items = result.getContent().stream()
                .map(entity -> new BoardSummary(toBoard(entity, List.of()), columns.countByBoard_Id(entity.getId())))
                .toList();
        return new BoardPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Board.Column> findColumn(Long columnId) {
        return columns.findById(columnId).map(this::toColumn);
    }

    @Override
    @Transactional
    public void renameColumn(Long boardId, Long columnId, String name) {
        BoardColumnEntity column = columns.findByIdAndBoard_Id(columnId, boardId)
                .orElseThrow(() -> dev.platform.board.domain.aggregate.ProjectRuleViolation.notFound(
                        "Column not found."));
        column.setName(name);
        columns.saveAndFlush(column);
    }

    @Override
    @Transactional
    public void saveColumnsInOrder(Long boardId, List<Board.Column> orderedColumns) {
        entityManager.flush();
        columns.moveAllToTemporaryPositions(boardId);
        entityManager.flush();
        entityManager.clear();

        BoardEntity board = boards.getReferenceById(boardId);
        for (int position = 0; position < orderedColumns.size(); position++) {
            Board.Column column = orderedColumns.get(position);
            if (column.id() == null) {
                columns.save(new BoardColumnEntity(null, board, column.name(), position));
            } else if (columns.setPosition(boardId, column.id(), position) != 1) {
                throw dev.platform.board.domain.aggregate.ProjectRuleViolation.notFound("Column not found.");
            }
        }
        columns.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public void deleteColumnAndReindex(Long boardId, Long columnId, List<Board.Column> remaining) {
        BoardColumnEntity target = columns.findByIdAndBoard_Id(columnId, boardId)
                .orElseThrow(() -> dev.platform.board.domain.aggregate.ProjectRuleViolation.notFound(
                        "Column not found."));
        columns.delete(target);
        columns.flush();
        entityManager.clear();

        columns.moveAllToTemporaryPositions(boardId);
        entityManager.flush();
        entityManager.clear();
        for (int position = 0; position < remaining.size(); position++) {
            Board.Column column = remaining.get(position);
            if (columns.setPosition(boardId, column.id(), position) != 1) {
                throw dev.platform.board.domain.aggregate.ProjectRuleViolation.notFound("Column not found.");
            }
        }
        columns.flush();
        entityManager.clear();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsInProject(Long projectId, String boardKey) {
        return boards.existsByProjectEntity_IdAndBoardKey(projectId, boardKey);
    }

    private Board toBoard(BoardEntity entity) {
        List<Board.Column> boardColumns = columns.findByBoard_IdOrderByPositionAsc(entity.getId())
                .stream().map(this::toColumn).toList();
        return toBoard(entity, boardColumns);
    }

    private Board toBoard(BoardEntity entity, List<Board.Column> boardColumns) {
        return new Board(entity.getId(), entity.getProjectEntity().getId(),
                entity.getName(), entity.getBoardKey(), Board.Status.valueOf(entity.getStatus()),
                entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getUpdatedAt(), boardColumns);
    }

    private Board.Column toColumn(BoardColumnEntity entity) {
        return new Board.Column(entity.getId(), entity.getBoard().getId(),
                entity.getName(), entity.getPosition());
    }
}
