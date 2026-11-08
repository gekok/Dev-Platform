package dev.platform.board.api.dto;

import dev.platform.board.domain.aggregate.Board;
import java.time.OffsetDateTime;
import java.util.List;

public record BoardResponse(
        Long id,
        Long projectId,
        String name,
        String boardKey,
        String status,
        OffsetDateTime createdAt,
        boolean writable,
        String myRole,
        List<ColumnResponse> columns) {

    public static BoardResponse from(Board board, boolean writable, String myRole) {
        return new BoardResponse(board.id(), board.projectId(), board.name(), board.key(),
                board.status().name(), board.createdAt(),
                writable, myRole, board.columns().stream().map(ColumnResponse::from).toList());
    }
}
