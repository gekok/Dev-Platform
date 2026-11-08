package dev.platform.user.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminCreateAccountRequestValidationTest {

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
  void rejectsFullNameLongerThanDatabaseColumn() {
    var request = new AdminCreateAccountRequest(
        "user@example.com", "Password123!", "n".repeat(256));

    assertThat(validator.validate(request))
        .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("fullName"));
  }

  @Test
  void rejectsEmailLongerThanDatabaseColumn() {
    var request = new AdminCreateAccountRequest(
        "a".repeat(244) + "@example.com", "Password123!", "User");

    assertThat(validator.validate(request))
        .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("email"));
  }

  @Test
  void rejectsLoginEmailLongerThanDatabaseColumn() {
    var request = new LoginRequest("a".repeat(244) + "@example.com", "Password123!");

    assertThat(validator.validate(request))
        .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("email"));
  }
}
