package dev.platform.board.application.query;

import dev.platform.board.domain.aggregate.Board;

public record BoardView(Board board, boolean writable, String myRole) {
}
