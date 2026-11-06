package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LoginUserServiceTest {

    // 1. Khai báo 3 diễn viên đóng thế
    private SaveUserPort users;
    private PasswordEncoderPort passwordEncoder;
    private TokenPort tokenPort;

    // 2. Class chính chúng ta cần test
    private LoginUserService loginService;

    @BeforeEach
    void setUp() {
        // Khởi tạo 3 mock đóng thế bằng lệnh mock(...) của Mockito
        users = mock(SaveUserPort.class);
        passwordEncoder = mock(PasswordEncoderPort.class);
        tokenPort = mock(TokenPort.class);

        // Nạp 3 diễn viên vào class chính
        loginService = new LoginUserService(users, passwordEncoder, tokenPort);
    }

    @Test
    void shouldLoginSuccessfullyWhenCredentialsAreValid() {
        // --- 1. ARRANGE (Chuẩn bị) ---
        // Tạo một user mẫu
        UserAggregate fakeUser = UserAggregate.create("test@example.com", "hashed_pass", "Ha Vu Long");
        LoginCommand command = new LoginCommand("test@example.com", "plain_pass");

        // Dặn dò diễn viên Mock bằng cú pháp: when(hành_động).thenReturn(kết_quả)
        when(users.findByEmail("test@example.com")).thenReturn(java.util.Optional.of(fakeUser));
        when(passwordEncoder.matches("plain_pass", "hashed_pass")).thenReturn(true);
        when(tokenPort.issue(fakeUser.getId(), fakeUser.getEmail())).thenReturn("fake-jwt-token");

        // --- 2. ACT (Hành động) ---
        String token = loginService.login(command);

        // --- 3. ASSERT (Khẳng định) ---
        // Khẳng định token trả về đúng là chuỗi mà tokenPort đã cấp
        assertThat(token).isEqualTo("fake-jwt-token");
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsIncorrect() {
        // 1. ARRANGE
        UserAggregate fakeUser = UserAggregate.create("test@example.com", "hashed_pass", "Ha Vu Long");
        LoginCommand command = new LoginCommand("test@example.com", "wrong_pass");

        when(users.findByEmail("test@example.com")).thenReturn(java.util.Optional.of(fakeUser));
        // Cho passwordEncoder giả vờ so sánh sai:
        when(passwordEncoder.matches("wrong_pass", "hashed_pass")).thenReturn(false);

        // 2 & 3. ACT & ASSERT
        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenEmailNotFound() {
        // 1. ARRANGE
        LoginCommand command = new LoginCommand("notfound@example.com", "any_password");
        when(users.findByEmail("notfound@example.com")).thenReturn(java.util.Optional.empty());

        // 2 & 3. ACT & ASSERT
        assertThatThrownBy(() -> loginService.login(command))
                .isInstanceOf(IllegalArgumentException.class);
    }
}