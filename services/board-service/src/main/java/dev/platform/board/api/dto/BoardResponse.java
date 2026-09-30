package dev.platform.board.api.dto;

import java.util.List;
import java.util.UUID;

import dev.platform.board.domain.aggregate.BoardAggregate;

// Phản hồi bảng kèm cột (kanban-api 2.1).
public record BoardResponse(
        Long id,
        String name,
        String key,
        UUID ownerId,
        List<ColumnResponse> columns
) {
    // Dựng từ Aggregate.
    public static BoardResponse from(BoardAggregate board) {
        List<ColumnResponse> columns = board.getColumns() == null ? List.of()
                : board.getColumns().stream()
                        .map(c -> new ColumnResponse(c.id(), c.name(), c.position()))
                        .toList();
        return new BoardResponse(
                board.getId(), board.getName(), board.getKey(), board.getOwnerId(), columns);
    }
}
