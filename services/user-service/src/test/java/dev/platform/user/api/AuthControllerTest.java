package dev.platform.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.infrastructure.security.JwtTokenAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock
  private LoginUserPort loginUserPort;

  @Mock
  private TokenPort tokenPort;

  @InjectMocks
  private AuthController authController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(authController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("POST /api/v1/auth/login thành công trả về 200 và Bearer token")
  void shouldLoginSuccessfully() throws Exception {
    when(loginUserPort.login(any(LoginCommand.class))).thenReturn("jwt.token.mock");

    String requestJson = """
        {
          "email": "user@demo.local",
          "password": "Password123!"
        }
        """;

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("jwt.token.mock"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/login sai mật khẩu trả về 401 INVALID_CREDENTIALS")
  void shouldReturn401WhenInvalidCredentials() throws Exception {
    when(loginUserPort.login(any(LoginCommand.class)))
        .thenThrow(new AuthenticationException("Invalid credentials"));

    String requestJson = """
        {
          "email": "user@demo.local",
          "password": "WrongPassword"
        }
        """;

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @DisplayName("POST /api/v1/auth/register trả về 400 OPERATION_NOT_SUPPORTED")
  void shouldReturn400ForDeprecatedRegister() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OPERATION_NOT_SUPPORTED"));
  }
}
