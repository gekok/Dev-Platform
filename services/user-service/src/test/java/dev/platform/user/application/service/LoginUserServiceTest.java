package dev.platform.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Locale;
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
    service = new LoginUserService(userRepository, tokenPort, guard, passwordEncoder);
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
  @DisplayName("Thất bại khi email không tồn tại")
  void shouldFailWhenEmailNotFound() {
    when(userRepository.findByEmail("unknown@demo.local")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login(new LoginCommand("unknown@demo.local", "pass")))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid credentials");
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
    when(passwordEncoder.matches("pass", "hashed_pass")).thenReturn(true);

    assertThatThrownBy(() -> service.login(new LoginCommand("user@demo.local", "pass")))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid credentials");
    verify(passwordEncoder).matches("pass", "hashed_pass");
  }

  @Test
  @DisplayName("Chuẩn hóa email đăng nhập ổn định dưới Turkish locale")
  void shouldNormalizeEmailIndependentlyOfDefaultLocale() {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      UserAggregate user = UserAggregate.rehydrate(
          UUID.randomUUID(), "admin@demo.local", "hashed_pass", "Admin", AccountStatus.ACTIVE, 1L, Set.of("USER"));

      when(userRepository.findByEmail("admin@demo.local")).thenReturn(Optional.of(user));
      when(passwordEncoder.matches("Password123!", "hashed_pass")).thenReturn(true);
      when(tokenPort.issue(user)).thenReturn("mocked.jwt.token");

      assertThat(service.login(new LoginCommand("ADMIN@demo.local", "Password123!")))
          .isEqualTo("mocked.jwt.token");
    } finally {
      Locale.setDefault(originalLocale);
    }
  }
}
