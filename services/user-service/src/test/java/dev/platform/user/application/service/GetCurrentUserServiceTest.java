package dev.platform.user.application.service;

import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCurrentUserServiceTest {

    private SaveUserPort users;
    private AccountAccessGuard guard;
    private GetCurrentUserService service;

    @BeforeEach
    void setUp() {
        users = mock(SaveUserPort.class);
        guard = new AccountAccessGuard();
        service = new GetCurrentUserService(users, guard);
    }

    @Test
    @DisplayName("Lấy hồ sơ thành công khi tài khoản tồn tại và ở trạng thái ACTIVE")
    void shouldReturnUserWhenExistsAndActive() {
        UUID userId = UUID.randomUUID();
        UserAggregate user = UserAggregate.rehydrate(
                userId, "test@example.com", "pass_hash", "Ha Vu Long", AccountStatus.ACTIVE, 1L);

        when(users.findById(userId)).thenReturn(Optional.of(user));

        UserAggregate result = service.me(userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getFullName()).isEqualTo("Ha Vu Long");
        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Từ chối khi userId không tồn tại trong cơ sở dữ liệu")
    void shouldThrowExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(userId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Từ chối khi tài khoản không ở trạng thái ACTIVE")
    void shouldThrowExceptionWhenUserIsNotActive() {
        UUID userId = UUID.randomUUID();
        UserAggregate inactiveUser = UserAggregate.rehydrate(
                userId, "test@example.com", "pass_hash", "Inactive User", null, 1L);

        when(users.findById(userId)).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> service.me(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account is not active");
    }

    @Test
    @DisplayName("Từ chối khi userId bị null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThatThrownBy(() -> service.me(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId cannot be null");
    }
}
