package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBoardRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,18}[a-z0-9]") String boardKey,
        Boolean createDefaultColumns) {
}
