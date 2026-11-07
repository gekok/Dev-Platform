package dev.platform.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.platform.user.application.port.out.AccountSnapshotSyncPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AccountNotFoundException;
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
class ChangeUserStatusServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private AccountSnapshotSyncPort snapshotSyncPort;

  private ChangeUserStatusService service;

  @BeforeEach
  void setUp() {
    service = new ChangeUserStatusService(userRepository, snapshotSyncPort);
  }

  @Test
  @DisplayName("Đổi trạng thái thành công thì lưu DB và đồng bộ snapshot")
  void shouldChangeStatusAndSyncSnapshot() {
    UUID userId = UUID.randomUUID();
    UserAggregate user = UserAggregate.rehydrate(
        userId, "user@demo.local", "pass", "User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

    service.changeStatus(userId, AccountStatus.SUSPENDED);

    assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    assertThat(user.getAuthVersion()).isEqualTo(2L);
    verify(userRepository).save(user);
    verify(snapshotSyncPort).recordStatusChange(userId, "SUSPENDED", Set.of("USER"), 2L);
  }

  @Test
  @DisplayName("Đổi sang trạng thái giống hiện tại thì không lưu DB và không đồng bộ snapshot")
  void shouldNotSyncWhenStatusIsIdentical() {
    UUID userId = UUID.randomUUID();
    UserAggregate user = UserAggregate.rehydrate(
        userId, "user@demo.local", "pass", "User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

    service.changeStatus(userId, AccountStatus.ACTIVE);

    verifyNoInteractions(snapshotSyncPort);
  }

  @Test
  @DisplayName("Gán role thành công thì tăng authVersion và đồng bộ role change")
  void shouldGrantRoleAndSync() {
    UUID userId = UUID.randomUUID();
    UserAggregate user = UserAggregate.rehydrate(
        userId, "user@demo.local", "pass", "User", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

    service.grantRole(userId, "SYSTEM_ADMIN");

    assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "SYSTEM_ADMIN");
    assertThat(user.getAuthVersion()).isEqualTo(2L);
    verify(userRepository).save(user);
    verify(snapshotSyncPort).recordSystemRolesChange(userId, "ACTIVE", Set.of("USER", "SYSTEM_ADMIN"), 2L);
  }

  @Test
  @DisplayName("Ném ngoại lệ khi user không tồn tại")
  void shouldThrowWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.changeStatus(userId, AccountStatus.LOCKED))
        .isInstanceOf(AccountNotFoundException.class);
  }
}
