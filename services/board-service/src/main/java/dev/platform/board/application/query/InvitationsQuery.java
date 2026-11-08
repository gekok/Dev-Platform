package dev.platform.board.application.query;

import java.util.UUID;

public record InvitationsQuery(Long projectId, String status, UUID actorId, int page, int size) {
}
