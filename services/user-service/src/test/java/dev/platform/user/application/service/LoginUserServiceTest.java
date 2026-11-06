package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginUserServiceTest {

    private SaveUserPort users;
    private PasswordEncoderPort passwordEncoder;
    private TokenPort tokenPort;
    private LoginUserService loginService;

    @BeforeEach
    void setUp() {
        users = mock(SaveUserPort.class);
        passwordEncoder = mock(PasswordEncoderPort.class);
        tokenPort = mock(TokenPort.class);
        loginService = new LoginUserService(users, passwordEncoder, tokenPort);
    }

    @Test
    @DisplayName("Đăng nhập thành công khi thông tin email và mật khẩu chính xác")
    void shouldLoginSuccessfullyWhenCredentialsAreValid() {
        UserAggregate fakeUser = UserAggregate.create("test@example.com", "hashed_pass", "Ha Vu Long");
        LoginCommand command = new LoginCommand("test@example.com", "plain_pass");

        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(fakeUser));
        when(passwordEncoder.matches("plain_pass", "hashed_pass")).thenReturn(true);
        when(tokenPort.issue(fakeUser.getId(), fakeUser.getEmail())).thenReturn("fake-rsa-jwt-token");

        String token = loginService.login(command);

        assertThat(token).isEqualTo("fake-rsa-jwt-token");
    }

    @Test
    @DisplayName("Từ chối khi mật khẩu không khớp")
    void shouldThrowExceptionWhenPasswordIsIncorrect() {
        UserAggregate fakeUser = UserAggregate.create("test@example.com", "hashed_pass", "Ha Vu Long");
        LoginCommand command = new LoginCommand("test@example.com", "wrong_pass");

        when(users.findByEmail("test@example.com")).thenReturn(Optional.of(fakeUser));
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Từ chối khi email không tồn tại nhưng vẫn chạy BCrypt giả để chống timing attack")
    void shouldThrowExceptionWhenEmailNotFoundAndExecuteDummyHashCheck() {
        LoginCommand command = new LoginCommand("notfound@example.com", "any_password");
        when(users.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid email or password");

        // Khẳng định passwordEncoder vẫn được gọi để triệt tiêu chênh lệch thời gian xử lý
        verify(passwordEncoder).matches(eq("any_password"), anyString());
    }

    @Test
    @DisplayName("Từ chối khi tài khoản không ở trạng thái ACTIVE")
    void shouldThrowExceptionWhenAccountIsNotActive() {
        UserAggregate inactiveUser = UserAggregate.rehydrate(
                UUID.randomUUID(), "inactive@example.com", "hashed_pass", "Inactive User", null, 1L);
        LoginCommand command = new LoginCommand("inactive@example.com", "plain_pass");

        when(users.findByEmail("inactive@example.com")).thenReturn(Optional.of(inactiveUser));
        when(passwordEncoder.matches("plain_pass", "hashed_pass")).thenReturn(true);

        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account is not active");
    }

    @Test
    @DisplayName("Từ chối khi command hoặc các trường bị null")
    void shouldThrowExceptionWhenInputIsNull() {
        assertThatThrownBy(() -> loginService.login(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loginService.login(new LoginCommand(null, "pass")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> loginService.login(new LoginCommand("test@example.com", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
