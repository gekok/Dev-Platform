package dev.platform.board.api.dto;

import jakarta.validation.constraints.Size;

public record RenameColumnRequest(@Size(max = 100) String name) {
}
