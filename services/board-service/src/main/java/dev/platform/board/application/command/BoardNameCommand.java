package dev.platform.board.application.command;

import java.util.UUID;

public record BoardNameCommand(Long boardId, String name, String boardKey, UUID actorId) {
}
