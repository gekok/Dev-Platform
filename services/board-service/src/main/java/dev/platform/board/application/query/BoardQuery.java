package dev.platform.board.application.query;

import java.util.UUID;

public record BoardQuery(Long boardId, UUID actorId) {
}
