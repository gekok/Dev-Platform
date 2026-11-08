package dev.platform.board.application.command;

import java.util.UUID;

public record RemoveMemberCommand(Long projectId, UUID memberId, UUID actorId) {
}
