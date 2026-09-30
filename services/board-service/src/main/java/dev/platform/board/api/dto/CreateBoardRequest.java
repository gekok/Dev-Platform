package dev.platform.board.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Yêu cầu tạo bảng (kanban-api 2.1).
public record CreateBoardRequest(
        @NotBlank(message = "name khong duoc trong") String name,
        @Pattern(regexp = "[A-Z0-9]{2,20}", message = "key 2-20 ky tu hoa so") String key,
        List<String> columns
) {
}
