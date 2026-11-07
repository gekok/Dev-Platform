package dev.platform.user.api.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.platform.user.domain.exception.AccessDeniedException;
import dev.platform.user.domain.exception.AccountNotFoundException;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.exception.EmailAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

  @RestController
  @RequestMapping("/test/errors")
  static class TestErrorController {
    @GetMapping("/auth")
    public void throwAuth() {
      throw new AuthenticationException("Bad credentials");
    }

    @GetMapping("/not-found")
    public void throwNotFound() {
      throw new AccountNotFoundException("Account missing");
    }

    @GetMapping("/email-exists")
    public void throwEmailExists() {
      throw new EmailAlreadyExistsException("Email duplicated");
    }

    @GetMapping("/data-integrity")
    public void throwDataIntegrity() {
      throw new DataIntegrityViolationException("Cannot insert duplicate key in dbo.users");
    }

    @GetMapping("/access-denied")
    public void throwAccessDenied() {
      throw new AccessDeniedException("SYSTEM_ADMIN required");
    }

    @GetMapping("/bad-request")
    public void throwBadRequest() {
      throw new IllegalArgumentException("Invalid state or parameter");
    }

    @GetMapping("/unsupported")
    public void throwUnsupported() {
      throw new UnsupportedOperationException("Not supported");
    }

    @GetMapping("/generic")
    public void throwGeneric() {
      throw new RuntimeException("Sensitive database stack trace or internal error");
    }

    @GetMapping("/not-readable")
    public void throwNotReadable() {
      throw new HttpMessageNotReadableException(
          "JSON parse error: Unexpected character",
          new org.springframework.mock.http.MockHttpInputMessage(new byte[0]));
    }

    @GetMapping("/type-mismatch")
    public void throwTypeMismatch() throws NoSuchMethodException {
      throw new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);
    }

    @GetMapping("/method-not-supported")
    public void throwMethodNotSupported() throws Exception {
      throw new HttpRequestMethodNotSupportedException("POST");
    }

    @GetMapping("/media-type-not-supported")
    public void throwMediaTypeNotSupported() throws Exception {
      throw new HttpMediaTypeNotSupportedException("application/xml");
    }

    @PostMapping("/post-only")
    public String postOnly() {
      return "ok";
    }
  }

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new TestErrorController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("AuthenticationException trả về 401 INVALID_CREDENTIALS")
  void shouldHandleAuthenticationException() throws Exception {
    mockMvc.perform(get("/test/errors/auth"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("Bad credentials"));
  }

  @Test
  @DisplayName("AccountNotFoundException trả về 404 ACCOUNT_NOT_FOUND")
  void shouldHandleAccountNotFoundException() throws Exception {
    mockMvc.perform(get("/test/errors/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Account missing"));
  }

  @Test
  @DisplayName("EmailAlreadyExistsException trả về 409 EMAIL_ALREADY_EXISTS")
  void shouldHandleEmailAlreadyExistsException() throws Exception {
    mockMvc.perform(get("/test/errors/email-exists"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.message").value("Email duplicated"));
  }

  @Test
  @DisplayName("DataIntegrityViolationException (trùng lặp đồng thời) trả về 409 EMAIL_ALREADY_EXISTS thay vì 500")
  void shouldHandleDataIntegrityViolationException() throws Exception {
    mockMvc.perform(get("/test/errors/data-integrity"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.message").value("A user with this email or unique identifier already exists"));
  }

  @Test
  @DisplayName("AccessDeniedException trả về 403 SYSTEM_ADMIN_REQUIRED")
  void shouldHandleAccessDeniedException() throws Exception {
    mockMvc.perform(get("/test/errors/access-denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_REQUIRED"))
        .andExpect(jsonPath("$.message").value("SYSTEM_ADMIN required"));
  }

  @Test
  @DisplayName("IllegalArgumentException trả về 400 BAD_REQUEST")
  void shouldHandleBadRequest() throws Exception {
    mockMvc.perform(get("/test/errors/bad-request"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  @DisplayName("HttpMessageNotReadableException (malformed payload) trả về 400 VALIDATION_FAILED thay vì 500")
  void shouldHandleHttpMessageNotReadableException() throws Exception {
    mockMvc.perform(get("/test/errors/not-readable"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("MethodArgumentTypeMismatchException trả về 400 VALIDATION_FAILED")
  void shouldHandleTypeMismatchException() throws Exception {
    mockMvc.perform(get("/test/errors/type-mismatch"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"));
  }

  @Test
  @DisplayName("HttpRequestMethodNotSupportedException trả về 405 METHOD_NOT_ALLOWED")
  void shouldHandleMethodNotSupportedException() throws Exception {
    mockMvc.perform(get("/test/errors/method-not-supported"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  @DisplayName("HttpMediaTypeNotSupportedException trả về 415 UNSUPPORTED_MEDIA_TYPE")
  void shouldHandleMediaTypeNotSupportedException() throws Exception {
    mockMvc.perform(get("/test/errors/media-type-not-supported"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  @DisplayName("Generic Exception trả về 500 INTERNAL_SERVER_ERROR nhưng không làm lộ chi tiết nội bộ")
  void shouldHandleGenericExceptionWithoutInformationLeakage() throws Exception {
    mockMvc.perform(get("/test/errors/generic"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("An unexpected internal error occurred"));
  }
}
