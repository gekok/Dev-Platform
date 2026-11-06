package dev.platform.user.api;

import dev.platform.user.api.exception.GlobalExceptionHandler;
import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.in.RegisterUserPort;
import dev.platform.user.application.port.out.TokenPort;
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
}
