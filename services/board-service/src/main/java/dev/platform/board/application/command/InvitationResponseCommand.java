package dev.platform.board.application.command;

import java.util.UUID;

public record InvitationResponseCommand(Long invitationId, UUID actorId) {
}
