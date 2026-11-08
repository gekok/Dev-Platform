package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeProjectStatusRequest(@NotBlank String status) {
}
