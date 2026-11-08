package dev.platform.user.domain.exception;

public class AuthorizationUnavailableException extends RuntimeException {
    public AuthorizationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthorizationUnavailableException(String message) {
        super(message);
    }
}
