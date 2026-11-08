package dev.platform.board.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateBoardRequest(@Size(max = 255) String name, @Size(max = 20) String boardKey) {
}
