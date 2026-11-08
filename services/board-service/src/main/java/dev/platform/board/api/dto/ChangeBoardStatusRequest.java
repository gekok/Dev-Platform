package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeBoardStatusRequest(@NotBlank String status) {
}
