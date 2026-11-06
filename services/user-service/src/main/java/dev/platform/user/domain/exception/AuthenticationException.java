package dev.platform.user.domain.exception;

/**
 * Ngoại lệ khi xác thực không thành công (thiếu token, token sai chữ ký, hết hạn).
 * Được GlobalExceptionHandler ánh xạ thành HTTP 401 UNAUTHORIZED.
 */
public class AuthenticationException extends DomainException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
