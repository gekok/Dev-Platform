package dev.platform.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AccountNotFoundException;
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
class GetCurrentUserServiceTest {

  @Mock
  private UserRepository userRepository;

  private final AccountAccessGuard guard = new AccountAccessGuard();
  private GetCurrentUserService service;

  @BeforeEach
  void setUp() {
    service = new GetCurrentUserService(userRepository, guard);
  }

  @Test
  @DisplayName("Lấy thông tin tài khoản hiện tại thành công khi tài khoản đang ACTIVE")
  void shouldReturnCurrentUserWhenActive() {
    UUID userId = UUID.randomUUID();
    UserAggregate user = UserAggregate.rehydrate(
        userId, "user@demo.local", "hash", "Demo User", AccountStatus.ACTIVE, 1L, Set.of("USER"));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserSummaryDto result = service.getCurrentUser(userId);

    assertThat(result.id()).isEqualTo(userId);
    assertThat(result.email()).isEqualTo("user@demo.local");
    assertThat(result.fullName()).isEqualTo("Demo User");
  }

  @Test
  @DisplayName("Ném AccountNotFoundException khi userId không tồn tại")
  void shouldThrowWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getCurrentUser(userId))
        .isInstanceOf(AccountNotFoundException.class)
        .hasMessageContaining("Account not found");
  }

  @Test
  @DisplayName("Ném AuthenticationException khi tài khoản bị khóa")
  void shouldThrowWhenUserIsLocked() {
    UUID userId = UUID.randomUUID();
    UserAggregate user = UserAggregate.rehydrate(
        userId, "locked@demo.local", "hash", "Locked User", AccountStatus.LOCKED, 1L, Set.of("USER"));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.getCurrentUser(userId))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Account is locked");
  }
}
