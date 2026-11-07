package dev.platform.user.api.exception;

import dev.platform.user.domain.exception.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Global Exception Handler gom lỗi về envelope chuẩn:
 * { "code": "...", "message": "...", "timestamp": "...", "path": "..." }
 * theo kanban-api.md mục 6.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi Bean Validation (@Valid) -> 400 BAD_REQUEST
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Du lieu khong hop le");
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request);
    }

    // Lỗi validate tham số nghiệp vụ -> 400 BAD_REQUEST
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
    }

    // Lỗi xác thực JWT hoặc sai credential -> 401 UNAUTHORIZED hoặc INVALID_CREDENTIALS
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            AuthenticationException ex, HttpServletRequest request) {
        String code = "Invalid email or password".equals(ex.getMessage()) ? "INVALID_CREDENTIALS" : "UNAUTHORIZED";
        return body(HttpStatus.UNAUTHORIZED, code, ex.getMessage(), request);
    }

    // Lỗi trạng thái tài khoản không được phép (ví dụ: tài khoản không ACTIVE) -> 403 FORBIDDEN
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            IllegalStateException ex, HttpServletRequest request) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    // Không tìm thấy tài khoản trong cơ sở dữ liệu -> 404 USER_NOT_FOUND
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoSuchElementException ex, HttpServletRequest request) {
        return body(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), request);
    }

    // Thao tác không được hỗ trợ (ví dụ: route đăng ký tự do đã bị bãi bỏ) -> 400 BAD_REQUEST
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupported(
            UnsupportedOperationException ex, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, "OPERATION_NOT_SUPPORTED", ex.getMessage(), request);
    }

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message != null ? message : status.getReasonPhrase());
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
