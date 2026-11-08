package dev.platform.board.application.query;

import java.util.UUID;

public record InvitationInboxQuery(String status, UUID actorId, int page, int size) {
}
