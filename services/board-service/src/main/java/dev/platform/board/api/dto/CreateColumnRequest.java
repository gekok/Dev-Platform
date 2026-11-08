package dev.platform.board.api.dto;

import jakarta.validation.constraints.Size;

public record CreateColumnRequest(@Size(max = 100) String name, Integer position) {
}
