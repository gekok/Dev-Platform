package dev.platform.board.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

// Yêu cầu xếp lại cột (kanban-api 2.5).
public record ReorderColumnsRequest(
        @NotEmpty(message = "orderedColumnIds khong duoc rong") List<Long> orderedColumnIds
) {
}
