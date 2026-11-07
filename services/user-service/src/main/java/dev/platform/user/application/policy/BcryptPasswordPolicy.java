package dev.platform.user.application.policy;

import java.nio.charset.StandardCharsets;

public final class BcryptPasswordPolicy {

    public static final int MAX_PASSWORD_UTF8_BYTES = 72;
    public static final String PASSWORD_TOO_LONG_MESSAGE =
            "Password must not exceed " + MAX_PASSWORD_UTF8_BYTES + " UTF-8 bytes";

    private BcryptPasswordPolicy() {
    }

    public static boolean isWithinLimit(String password) {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_UTF8_BYTES;
    }

    public static void requireWithinLimit(String password) {
        if (!isWithinLimit(password)) {
            throw new IllegalArgumentException(PASSWORD_TOO_LONG_MESSAGE);
        }
    }
}
