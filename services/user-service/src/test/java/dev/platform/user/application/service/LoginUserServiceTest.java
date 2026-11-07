package dev.platform.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoderPort passwordEncoder;

  @Mock
  private TokenPort tokenPort;

  private final AccountAccessGuard guard = new AccountAccessGuard();
  private LoginUserService service;

  @BeforeEach
  void setUp() {
    service = new LoginUserService(userRepository, guard, passwordEncoder, tokenPort);
  }

  @Test
  @DisplayName("Đăng nhập thành công trả về access token")
  void shouldLoginSuccessfully() {
    UserAggregate user = UserAggregate.rehydrate(
        UUID.randomUUID(), "user@demo.local", "hashed_pass", "User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(userRepository.findByEmail("user@demo.local")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Password123!", "hashed_pass")).thenReturn(true);
    when(tokenPort.issue(user)).thenReturn("mocked.jwt.token");

    String token = service.login(new LoginCommand("user@demo.local", "Password123!"));

    assertThat(token).isEqualTo("mocked.jwt.token");
    verify(tokenPort).issue(user);
  }

  @Test
  @DisplayName("Thất bại khi email không tồn tại và thực hiện dummy hash check để ngăn timing attack")
  void shouldFailWhenEmailNotFound() {
    when(userRepository.findByEmail("unknown@demo.local")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginCommand("unknown@demo.local", "pass")))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid credentials");

    verify(passwordEncoder).matches(org.mockito.ArgumentMatchers.eq("pass"), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("Thất bại khi mật khẩu sai")
  void shouldFailWhenPasswordMismatch() {
    UserAggregate user = UserAggregate.rehydrate(
        UUID.randomUUID(), "user@demo.local", "hashed_pass", "User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(userRepository.findByEmail("user@demo.local")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("WrongPass", "hashed_pass")).thenReturn(false);

    assertThatThrownBy(() -> service.login(new LoginCommand("user@demo.local", "WrongPass")))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid credentials");
  }

  @Test
  @DisplayName("Thất bại khi tài khoản bị khóa")
  void shouldFailWhenAccountLocked() {
    UserAggregate user = UserAggregate.rehydrate(
        UUID.randomUUID(), "user@demo.local", "hashed_pass", "User", AccountStatus.LOCKED, 1L, Set.of("USER"));

    when(userRepository.findByEmail("user@demo.local")).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.login(new LoginCommand("user@demo.local", "pass")))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Account is locked");
  }
}
