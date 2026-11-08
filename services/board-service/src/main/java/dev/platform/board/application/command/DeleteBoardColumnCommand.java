package dev.platform.board.application.command;

import java.util.UUID;

public record DeleteBoardColumnCommand(Long columnId, UUID actorId) {
}
