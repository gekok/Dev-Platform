package dev.platform.board.api.dto;

import dev.platform.board.domain.aggregate.Board;
import java.time.OffsetDateTime;

public record BoardSummaryResponse(
        Long id,
        Long projectId,
        String name,
        String boardKey,
        String status,
        OffsetDateTime createdAt,
        long columnCount) {

    public static BoardSummaryResponse from(Board board, long columnCount) {
        return new BoardSummaryResponse(board.id(), board.projectId(), board.name(),
                board.key(), board.status().name(), board.createdAt(), columnCount);
    }
}
