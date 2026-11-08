package dev.platform.board.application.query;

import dev.platform.board.domain.aggregate.Board;
import java.util.List;

public record BoardAccess(
        Long boardId,
        Long projectId,
        String boardStatus,
        String projectStatus,
        List<Board.Column> columns) {
}
