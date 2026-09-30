package dev.platform.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// DTO đăng nhập.
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password) {
}
