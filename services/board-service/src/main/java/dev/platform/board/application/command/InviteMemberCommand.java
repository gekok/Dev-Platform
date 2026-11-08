package dev.platform.board.application.command;

import java.util.UUID;

public record InviteMemberCommand(Long projectId, UUID inviteeId, UUID actorId) {
}
