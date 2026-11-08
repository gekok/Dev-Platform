package dev.platform.board.application.command;

import java.util.UUID;

public record AddBoardColumnCommand(Long boardId, String name, Integer position, UUID actorId) {
}
