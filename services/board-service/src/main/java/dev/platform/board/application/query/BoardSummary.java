package dev.platform.board.application.query;

import dev.platform.board.domain.aggregate.Board;

public record BoardSummary(Board board, long columnCount) {
}
