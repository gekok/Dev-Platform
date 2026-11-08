package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderBoardColumnsRequest(@NotEmpty List<@NotNull Long> columnIds) {
}
