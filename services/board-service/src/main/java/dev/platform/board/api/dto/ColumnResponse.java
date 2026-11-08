package dev.platform.board.api.dto;

import dev.platform.board.domain.aggregate.Board;

public record ColumnResponse(Long id, Long boardId, String name, int position) {
    public static ColumnResponse from(Board.Column column) {
        return new ColumnResponse(column.id(), column.boardId(), column.name(), column.position());
    }
}
