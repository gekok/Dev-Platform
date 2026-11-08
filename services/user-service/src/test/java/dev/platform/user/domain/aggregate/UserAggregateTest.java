package dev.platform.user.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.platform.user.domain.AccountStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserAggregateTest {

  @Test
  @DisplayName("Khởi tạo user mới mặc định có status ACTIVE, authVersion 1 và role USER")
  void shouldCreateNewUserWithDefaultActiveAndRoleUser() {
    UserAggregate user = UserAggregate.create("user@example.com", "hashed_pass", "John Doe", null);

    assertThat(user.getId()).isNotNull();
    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getFullName()).isEqualTo("John Doe");
    assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(user.getAuthVersion()).isEqualTo(1L);
    assertThat(user.getRoles()).containsExactly("USER");
  }

  @Test
  @DisplayName("Đổi trạng thái sang SUSPENDED làm tăng authVersion")
  void shouldIncrementAuthVersionWhenStatusChanges() {
    UserAggregate user = UserAggregate.create("user@example.com", "hashed_pass", "John Doe", Set.of("USER"));

    boolean changed = user.changeStatus(AccountStatus.SUSPENDED);

    assertThat(changed).isTrue();
    assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
    assertThat(user.getAuthVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("Gán role SYSTEM_ADMIN thành công và làm tăng authVersion")
  void shouldGrantSystemAdminRoleAndIncrementAuthVersion() {
    UserAggregate user = UserAggregate.create("user@example.com", "hashed_pass", "John Doe", null);

    boolean changed = user.grantRole("SYSTEM_ADMIN");

    assertThat(changed).isTrue();
    assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "SYSTEM_ADMIN");
    assertThat(user.getAuthVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("Thu hồi role thành công và làm tăng authVersion")
  void shouldRevokeRoleAndIncrementAuthVersion() {
    UserAggregate user = UserAggregate.create("user@example.com", "hashed_pass", "John Doe", Set.of("USER", "SYSTEM_ADMIN"));

    boolean changed = user.revokeRole("SYSTEM_ADMIN");

    assertThat(changed).isTrue();
    assertThat(user.getRoles()).containsExactly("USER");
    assertThat(user.getAuthVersion()).isEqualTo(2L);
  }

  @Test
  @DisplayName("Ném exception khi thu hồi role cuối cùng của user")
  void shouldThrowWhenRevokingLastRemainingRole() {
    UserAggregate user = UserAggregate.create("user@example.com", "hashed_pass", "John Doe", Set.of("USER"));

    assertThatThrownBy(() -> user.revokeRole("USER"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
