package dev.platform.user.api.exception;

import dev.platform.user.api.dto.ErrorResponse;
import dev.platform.user.domain.exception.AccessDeniedException;
import dev.platform.user.domain.exception.AccountNotFoundException;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.exception.EmailAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse("INVALID_CREDENTIALS", ex.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(AccountNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleAccountNotFound(
      AccountNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("ACCOUNT_NOT_FOUND", ex.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(EmailAlreadyExistsException.class)
  public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
      EmailAlreadyExistsException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("EMAIL_ALREADY_EXISTS", ex.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("SYSTEM_ADMIN_REQUIRED", ex.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(
      IllegalArgumentException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("BAD_REQUEST", ex.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .reduce((a, b) -> a + "; " + b)
        .orElse("Validation failed");

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_FAILED", message, OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.warn("Malformed HTTP request body at {}: {}", request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_FAILED", "Malformed JSON request body or invalid field format", OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    log.warn("Type mismatch for parameter '{}' at {}: {}", ex.getName(), request.getRequestURI(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("VALIDATION_FAILED", "Invalid value for parameter: " + ex.getName(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(new ErrorResponse("METHOD_NOT_ALLOWED", "HTTP method " + ex.getMethod() + " is not supported for this endpoint", OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "Content type " + ex.getContentType() + " is not supported", OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ErrorResponse> handleUnsupported(
      UnsupportedOperationException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("OPERATION_NOT_SUPPORTED", ex.getMessage(), OffsetDateTime.now(), request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(
      Exception ex, HttpServletRequest request) {
    log.error("Unhandled internal server error at {}:", request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected internal error occurred", OffsetDateTime.now(), request.getRequestURI()));
  }
}
