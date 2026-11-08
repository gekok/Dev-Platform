package dev.platform.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record AdminCreateAccountRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password must not be blank")
    String password,

    @NotBlank(message = "Full name must not be blank")
    String fullName,

    Set<String> roles
) {
}
