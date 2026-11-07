package dev.platform.user.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void acceptsPasswordAt72Utf8Bytes() {
        assertThat(validate("a".repeat(72))).isEmpty();
        assertThat(validate("ấ".repeat(24))).isEmpty();
    }

    @Test
    void rejectsPasswordOver72Utf8BytesOnPasswordField() {
        assertPasswordLengthViolation("a".repeat(73));
        assertPasswordLengthViolation("ấ".repeat(25));
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(String password) {
        return validator.validate(new RegisterRequest("user@example.com", password, "User"));
    }

    private void assertPasswordLengthViolation(String password) {
        Set<ConstraintViolation<RegisterRequest>> violations = validate(password);

        assertThat(violations).hasSize(1);
        ConstraintViolation<RegisterRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
        assertThat(violation.getMessage()).contains("72 UTF-8 bytes");
    }
}
