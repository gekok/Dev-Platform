package dev.platform.user.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.platform.user.api.validation.BcryptCompatiblePassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminCreateAccountRequest(
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @BcryptCompatiblePassword
    String password,

    @NotBlank(message = "Full name must not be blank")
    String fullName
) {
}
