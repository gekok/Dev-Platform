package dev.platform.notification.api.exception;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Lỗi chuẩn {code,message,timestamp,path} (kanban-api mục 6).
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validate @Valid lỗi -> 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Du lieu khong hop le");
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, req);
    }

    // Validate tay trong Aggregate -> 400.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req);
    }

    // Không thấy thông báo -> 404.
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoSuchElementException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", ex.getMessage(), req);
    }

    // Đọc nhầm thông báo người khác -> 403.
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            SecurityException ex, HttpServletRequest req) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), req);
    }

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String code, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message == null ? status.getReasonPhrase() : message,
                "timestamp", OffsetDateTime.now().toString(),
                "path", req.getRequestURI()));
    }
}
