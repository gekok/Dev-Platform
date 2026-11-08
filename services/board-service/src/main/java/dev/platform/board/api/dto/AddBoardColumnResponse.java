package dev.platform.board.api.dto;

import java.util.List;

public record AddBoardColumnResponse(ColumnResponse column, List<ColumnResponse> columns) {
}
