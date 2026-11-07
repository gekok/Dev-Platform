package dev.platform.user.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateUserRolesRequest(
    @NotEmpty(message = "roles cannot be empty")
    Set<String> roles
) {}
