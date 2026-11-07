package dev.platform.user.api;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.in.RegisterUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private MockMvc mvc;
    private LoginUserPort loginPort;
    private GetCurrentUserPort mePort;
    private TokenPort tokenPort;
    private RegisterUserPort registerPort;

    @BeforeEach
    void setUp() {
        loginPort = mock(LoginUserPort.class);
        mePort = mock(GetCurrentUserPort.class);
        tokenPort = mock(TokenPort.class);
        registerPort = null; // Deprecated theo quyết định #7

        UserController controller = new UserController(registerPort, loginPort, mePort, tokenPort);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Thành công khi gửi token hợp lệ, trả đúng profile tối thiểu")
    void shouldReturnProfileWhenTokenIsValid() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                authenticatedUserId, "alex@example.com", "secret_hash", "Alex Morgan", AccountStatus.ACTIVE, 1L);

        when(tokenPort.parseAndValidate("valid.jwt.token")).thenReturn(authenticatedUserId);
        when(mePort.me(authenticatedUserId)).thenReturn(user);

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(authenticatedUserId.toString()))
                .andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.fullName").value("Alex Morgan"))
                // Khẳng định không lộ password hash
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Header X-User-Id hoặc query param giả mạo không thể thay đổi Account được đọc")
    void shouldIgnoreSpoofedHeadersAndUseTokenSubjectOnly() throws Exception {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID attackerSpoofedId = UUID.randomUUID();

        UserAggregate user = UserAggregate.rehydrate(
                authenticatedUserId, "legit@example.com", "secret_hash", "Legit User", AccountStatus.ACTIVE, 1L);

        when(tokenPort.parseAndValidate("valid.jwt.token")).thenReturn(authenticatedUserId);
        when(mePort.me(authenticatedUserId)).thenReturn(user);

        // Kẻ tấn công cố tình truyền X-User-Id hoặc query userId để giả danh
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid.jwt.token")
                        .header("X-User-Id", attackerSpoofedId.toString())
                        .param("userId", attackerSpoofedId.toString()))
                .andExpect(status().isOk())
                // Kết quả vẫn bắt buộc là authenticatedUserId từ token
                .andExpect(jsonPath("$.id").value(authenticatedUserId.toString()))
                .andExpect(jsonPath("$.email").value("legit@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Trả 401 UNAUTHORIZED khi thiếu Authorization header")
    void shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Trả 401 UNAUTHORIZED khi Authorization header không có tiền tố Bearer")
    void shouldReturn401WhenAuthorizationHeaderNotBearer() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Basic abcxyz123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Trả 401 UNAUTHORIZED khi token sai chữ ký hoặc hết hạn")
    void shouldReturn401WhenTokenIsInvalidOrExpired() throws Exception {
        when(tokenPort.parseAndValidate("invalid.token"))
                .thenThrow(new AuthenticationException("Invalid or expired token"));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Trả 404 USER_NOT_FOUND khi tài khoản không tồn tại trong DB")
    void shouldReturn404WhenUserNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenPort.parseAndValidate("valid.token")).thenReturn(userId);
        when(mePort.me(userId)).thenThrow(new NoSuchElementException("User not found: " + userId));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found: " + userId));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Trả 403 FORBIDDEN khi tài khoản không ở trạng thái ACTIVE")
    void shouldReturn403WhenAccountIsNotActive() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenPort.parseAndValidate("valid.token")).thenReturn(userId);
        when(mePort.me(userId)).thenThrow(new IllegalStateException("Account is not active"));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Account is not active"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: Thành công trả về TokenResponse")
    void shouldLoginSuccessfully() throws Exception {
        when(loginPort.login(any())).thenReturn("sample.rsa256.jwt");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("sample.rsa256.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/public-key: Trả về Public Key RSA256 ở định dạng PEM cho Gateway xác thực")
    void shouldReturnPublicKeyPem() throws Exception {
        dev.platform.user.infrastructure.security.JwtTokenAdapter realAdapter =
                new dev.platform.user.infrastructure.security.JwtTokenAdapter(3600000L);
        UserController customController = new UserController(registerPort, loginPort, mePort, realAdapter);
        MockMvc customMvc = MockMvcBuilders.standaloneSetup(customController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        customMvc.perform(get("/api/v1/auth/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("RSA256"))
                .andExpect(jsonPath("$.publicKey").value(org.hamcrest.Matchers.containsString("BEGIN PUBLIC KEY")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register: Trả 400 OPERATION_NOT_SUPPORTED do đã bị bãi bỏ theo quyết định #7")
    void shouldReturn400ForDeprecatedRegister() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"secret123\",\"fullName\":\"Alex Morgan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_SUPPORTED"));
    }
}
