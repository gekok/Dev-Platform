package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(@NotNull UUID newOwnerUserId) {
}
