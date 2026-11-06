package dev.platform.user.api;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.in.RegisterUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@ContextConfiguration(classes = {
        UserController.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoginUserPort loginPort;

    @MockitoBean
    private TokenPort tokenPort;

    @MockitoBean
    private GetCurrentUserPort mePort;

    @MockitoBean
    private RegisterUserPort registerPort;

    @Test
    @DisplayName("POST /api/v1/auth/login: Đi qua Spring Security filter chain và trả 200 OK mà không bị HTTP Basic chặn")
    void shouldPermitLoginThroughSecurityFilterChain() throws Exception {
        when(loginPort.login(any(LoginCommand.class))).thenReturn("valid.rsa256.jwt");

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.accessToken").value("valid.rsa256.jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/public-key: Cho phép truy cập công khai qua Spring Security filter chain")
    void shouldPermitPublicKeyThroughSecurityFilterChain() throws Exception {
        mvc.perform(get("/api/v1/auth/public-key"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login: Sai email hoặc mật khẩu trả về 401 với mã INVALID_CREDENTIALS")
    void shouldReturn401InvalidCredentialsOnLoginFailure() throws Exception {
        when(loginPort.login(any(LoginCommand.class)))
                .thenThrow(new dev.platform.user.domain.exception.AuthenticationException("Invalid email or password"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@example.com\",\"password\":\"wrong_pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Đi qua Security filter chain và trả 200 OK khi có Bearer token hợp lệ")
    void shouldPermitCurrentUserThroughSecurityFilterChainWithBearerToken() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                userId, "alex@example.com", "hash", "Alex Morgan", AccountStatus.ACTIVE, 1L);
        when(tokenPort.parseAndValidate("valid.token")).thenReturn(userId);
        when(mePort.me(userId)).thenReturn(user);

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("alex@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me: Đến được UserController và trả 401 envelope khi thiếu Authorization, không bị Basic Auth chặn")
    void shouldReachControllerAndReturn401EnvelopeWhenMissingAuthHeader() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("Route ngoài allowlist bị chặn mặc định bởi SecurityFilterChain (denyAll)")
    void shouldDenyUnconfiguredRoutesByDefault() throws Exception {
        mvc.perform(get("/api/v1/users/profiles"))
                .andExpect(status().isForbidden());
    }
}
