package dev.platform.board.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteMemberRequest(@NotNull UUID userId) {
}
