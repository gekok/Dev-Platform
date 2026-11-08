package dev.platform.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.platform.user.application.command.AdminCreateAccountCommand;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.out.AccountSnapshotSyncPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.EmailAlreadyExistsException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminCreateAccountServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoderPort passwordEncoder;

  @Mock
  private AccountSnapshotSyncPort snapshotSyncPort;

  private AdminCreateAccountService service;

  @BeforeEach
  void setUp() {
    service = new AdminCreateAccountService(userRepository, passwordEncoder, snapshotSyncPort);
  }

  @Test
  @DisplayName("Admin tạo tài khoản mới thành công và đồng bộ snapshot với role USER mặc định")
  void shouldCreateAccountSuccessfully() {
    AdminCreateAccountCommand cmd = new AdminCreateAccountCommand(
        "newuser@demo.local", "PlainPassword123", "New User");

    when(userRepository.existsByEmail("newuser@demo.local")).thenReturn(false);
    when(passwordEncoder.encode("PlainPassword123")).thenReturn("bcrypt_hash");
    when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UserSummaryDto result = service.createAccount(cmd);

    assertThat(result.email()).isEqualTo("newuser@demo.local");
    assertThat(result.fullName()).isEqualTo("New User");
    assertThat(result.roles()).containsExactly("USER");
    verify(userRepository).save(any(UserAggregate.class));
    verify(snapshotSyncPort).recordStatusChange(result.id(), "ACTIVE", Set.of("USER"), 1L);
  }

  @Test
  @DisplayName("Thất bại khi email đã tồn tại")
  void shouldThrowWhenEmailAlreadyExists() {
    AdminCreateAccountCommand cmd = new AdminCreateAccountCommand(
        "existing@demo.local", "pass", "Existing");

    when(userRepository.existsByEmail("existing@demo.local")).thenReturn(true);

    assertThatThrownBy(() -> service.createAccount(cmd))
        .isInstanceOf(EmailAlreadyExistsException.class)
        .hasMessageContaining("Email already exists");
  }

  @Test
  @DisplayName("Chuẩn hóa email tạo tài khoản ổn định dưới Turkish locale")
  void shouldNormalizeEmailIndependentlyOfDefaultLocale() {
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      when(userRepository.existsByEmail("admin@demo.local")).thenReturn(false);
      when(passwordEncoder.encode("Password123!")).thenReturn("bcrypt_hash");
      when(userRepository.save(any(UserAggregate.class))).thenAnswer(invocation -> invocation.getArgument(0));

      UserSummaryDto result = service.createAccount(
          new AdminCreateAccountCommand("ADMIN@demo.local", "Password123!", "Admin"));

      assertThat(result.email()).isEqualTo("admin@demo.local");
      verify(userRepository).existsByEmail("admin@demo.local");
    } finally {
      Locale.setDefault(originalLocale);
    }
  }
}
