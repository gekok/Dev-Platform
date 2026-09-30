package dev.platform.task.api.exception;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

// Bắt lỗi validate Aggregate, trả 400 theo kanban-api.md mục 6.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi validate đầu vào (title trống, position âm...).
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                OffsetDateTime.now().toString(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Body lỗi chuẩn mục 6 kanban-api.md.
    public record ErrorResponse(String code, String message, String timestamp, String path) {
    }
}
