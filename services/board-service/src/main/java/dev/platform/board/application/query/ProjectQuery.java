package dev.platform.board.application.query;

import java.util.UUID;

public record ProjectQuery(Long projectId, UUID actorId) {
}
