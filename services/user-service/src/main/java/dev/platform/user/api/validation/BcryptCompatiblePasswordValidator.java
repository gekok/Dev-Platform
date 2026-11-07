package dev.platform.user.api.validation;

import dev.platform.user.application.policy.BcryptPasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class BcryptCompatiblePasswordValidator
        implements ConstraintValidator<BcryptCompatiblePassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return BcryptPasswordPolicy.isWithinLimit(password);
    }
}
