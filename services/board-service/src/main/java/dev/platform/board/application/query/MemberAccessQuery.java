package dev.platform.board.application.query;

import java.util.UUID;

public record MemberAccessQuery(Long projectId, UUID userId) {
}
