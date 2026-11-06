package dev.platform.user.api.exception;

import dev.platform.user.domain.exception.AuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Gom lỗi về format {code,message,timestamp,path} theo kanban-api.md mục 6.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi validate nghiệp vụ -> 400.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "BAD_REQUEST");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Sai thông tin đăng nhập hoặc lỗi xác thực -> 401.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            AuthenticationException ex, HttpServletRequest request) {
        String code = "Invalid email or password".equals(ex.getMessage()) ? "INVALID_CREDENTIALS" : "UNAUTHORIZED";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}
