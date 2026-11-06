package dev.platform.user.application.service;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.repository.UserRepository;
import dev.platform.user.infrastructure.snapshot.AccountAuthorizationSnapshotSyncRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeUserStatusServiceTest {

    private UserRepository userRepository;
    private AccountAuthorizationSnapshotSyncRecorder snapshotSyncRecorder;
    private ChangeUserStatusService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        snapshotSyncRecorder = mock(AccountAuthorizationSnapshotSyncRecorder.class);
        service = new ChangeUserStatusService(userRepository, snapshotSyncRecorder);
    }

    @Test
    @DisplayName("Đổi trạng thái tài khoản sang SUSPENDED: Lưu DB và ghi nhận Outbox + Snapshot job nguyên tử")
    void shouldChangeStatusAndRecordOutboxAndSnapshotJobAtomically() {
        UUID userId = UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                userId, "alex@example.com", "hash", "Alex Morgan", AccountStatus.ACTIVE, 1L, Set.of("USER"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.changeStatus(userId, AccountStatus.SUSPENDED);

        verify(userRepository).save(user);
        verify(snapshotSyncRecorder).recordStatusChange(
                eq(userId), eq("SUSPENDED"), eq(Set.of("USER")), eq(2L));
        assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(user.getAuthVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Nếu trạng thái không đổi: Không lưu DB và không gọi recorder")
    void shouldNotRecordWhenStatusIsUnchanged() {
        UUID userId = UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                userId, "alex@example.com", "hash", "Alex Morgan", AccountStatus.ACTIVE, 1L, Set.of("USER"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.changeStatus(userId, AccountStatus.ACTIVE);

        verify(userRepository, never()).save(any());
        verify(snapshotSyncRecorder, never()).recordStatusChange(any(), any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("Gán role SYSTEM_ADMIN: Cập nhật role, tăng version và ghi nhận Outbox + Snapshot job")
    void shouldGrantRoleAndRecordOutboxAndSnapshotJobAtomically() {
        UUID userId = UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                userId, "alex@example.com", "hash", "Alex Morgan", AccountStatus.ACTIVE, 1L, Set.of("USER"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.grantRole(userId, "SYSTEM_ADMIN", null);

        verify(userRepository).save(user);
        verify(snapshotSyncRecorder).recordSystemRolesChange(
                eq(userId), eq("ACTIVE"), eq(Set.of("USER", "SYSTEM_ADMIN")), eq(2L));
        assertThat(user.getSystemRoles()).containsExactlyInAnyOrder("USER", "SYSTEM_ADMIN");
        assertThat(user.getAuthVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Thu hồi role: Cập nhật role, tăng version và ghi nhận Outbox + Snapshot job")
    void shouldRevokeRoleAndRecordOutboxAndSnapshotJobAtomically() {
        UUID userId = UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                userId, "admin@example.com", "hash", "Admin", AccountStatus.ACTIVE, 1L, Set.of("USER", "SYSTEM_ADMIN"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.revokeRole(userId, "SYSTEM_ADMIN");

        verify(userRepository).save(user);
        verify(snapshotSyncRecorder).recordSystemRolesChange(
                eq(userId), eq("ACTIVE"), eq(Set.of("USER")), eq(2L));
        assertThat(user.getSystemRoles()).containsExactly("USER");
        assertThat(user.getAuthVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Ném NoSuchElementException khi không tìm thấy user ID")
    void shouldThrowNoSuchElementExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStatus(userId, AccountStatus.SUSPENDED))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(userId.toString());

        assertThatThrownBy(() -> service.grantRole(userId, "SYSTEM_ADMIN", null))
                .isInstanceOf(NoSuchElementException.class);

        assertThatThrownBy(() -> service.getRoles(userId))
                .isInstanceOf(NoSuchElementException.class);
    }
}
