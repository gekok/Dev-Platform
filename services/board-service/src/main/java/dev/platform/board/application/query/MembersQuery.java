package dev.platform.board.application.query;

import java.util.UUID;

public record MembersQuery(Long projectId, UUID actorId, int page, int size) {
}
