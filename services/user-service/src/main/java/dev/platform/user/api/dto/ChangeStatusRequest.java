package dev.platform.user.api.dto;

import dev.platform.user.domain.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
    @NotNull(message = "Status must not be null")
    AccountStatus status
) {
}
