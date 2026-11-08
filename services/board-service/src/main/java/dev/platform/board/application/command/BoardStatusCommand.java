package dev.platform.board.application.command;

import dev.platform.board.domain.aggregate.Board;
import java.util.UUID;

public record BoardStatusCommand(Long boardId, Board.Status status, UUID actorId) {
}
