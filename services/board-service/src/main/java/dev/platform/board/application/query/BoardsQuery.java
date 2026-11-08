package dev.platform.board.application.query;

import java.util.UUID;

public record BoardsQuery(
        Long projectId,
        String status,
        int page,
        int size,
        String sort,
        UUID actorId) {
}
