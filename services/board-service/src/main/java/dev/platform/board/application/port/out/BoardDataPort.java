package dev.platform.board.application.port.out;

import dev.platform.board.application.query.BoardPage;
import dev.platform.board.application.query.BoardSummary;
import dev.platform.board.domain.aggregate.Board;
import java.util.List;
import java.util.Optional;

public interface BoardDataPort {
    Board save(Board board);

    Optional<Board> find(Long boardId);

    Optional<Board> findForUpdate(Long boardId);

    BoardPage<BoardSummary> list(Long projectId, String status, int page, int size, String sort);

    Optional<Board.Column> findColumn(Long columnId);

    void renameColumn(Long boardId, Long columnId, String name);

    void saveColumnsInOrder(Long boardId, List<Board.Column> columns);

    void deleteColumnAndReindex(Long boardId, Long columnId, List<Board.Column> remaining);

    boolean existsInProject(Long projectId, String boardKey);
}
