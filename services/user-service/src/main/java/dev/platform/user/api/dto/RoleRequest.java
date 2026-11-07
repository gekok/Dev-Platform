package dev.platform.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
    @NotBlank(message = "Role must not be blank")
    String role
) {
}
