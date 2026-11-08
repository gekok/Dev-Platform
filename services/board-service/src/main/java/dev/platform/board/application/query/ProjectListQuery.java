package dev.platform.board.application.query;

import java.util.UUID;

public record ProjectListQuery(UUID actorId, int page, int size) {
}
