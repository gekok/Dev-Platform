package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotBlank;

// Yêu cầu thêm cột (kanban-api 2.4).
public record AddColumnRequest(
        @NotBlank(message = "name khong duoc trong") String name
) {
}
