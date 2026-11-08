package dev.platform.board.application.command;

import java.util.UUID;

public record CancelInvitationCommand(Long projectId, Long invitationId, UUID actorId) {
}
