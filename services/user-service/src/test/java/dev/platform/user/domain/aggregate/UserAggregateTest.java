package dev.platform.user.domain.aggregate;

import dev.platform.user.domain.AccountStatus;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAggregateTest {

    @Nested
    @DisplayName("Tạo mới UserAggregate (create)")
    class CreateTests {

        @Test
        @DisplayName("Tạo User thành công với giá trị mặc định role USER và authVersion = 1")
        void shouldCreateUserWithDefaultRoleAndVersion() {
            UserAggregate user = UserAggregate.create(
                    "  Alice@EXAMPLE.com ",
                    "hashedPassword123",
                    " Alice Smith ",
                    null
            );

            assertThat(user.getId()).isNotNull();
            assertThat(user.getEmail()).isEqualTo("alice@example.com");
            assertThat(user.getPasswordHash()).isEqualTo("hashedPassword123");
            assertThat(user.getFullName()).isEqualTo("Alice Smith");
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.getAuthVersion()).isEqualTo(1L);
            assertThat(user.getRoles()).containsExactly("USER");
        }

        @Test
        @DisplayName("Tạo User với tập role tùy biến")
        void shouldCreateUserWithCustomRoles() {
            UserAggregate user = UserAggregate.create(
                    "bob@example.com",
                    "hash",
                    "Bob",
                    Set.of("USER", "SYSTEM_ADMIN")
            );

            assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "SYSTEM_ADMIN");
        }

        @Test
        @DisplayName("Ném NullPointerException khi thiếu các trường bắt buộc")
        void shouldThrowWhenRequiredFieldsMissing() {
            assertThatThrownBy(() -> UserAggregate.create(null, "hash", "Name", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("email cannot be null");

            assertThatThrownBy(() -> UserAggregate.create("a@b.com", null, "Name", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("passwordHash cannot be null");

            assertThatThrownBy(() -> UserAggregate.create("a@b.com", "hash", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fullName cannot be null");
        }
    }

    @Nested
    @DisplayName("Khôi phục UserAggregate (rehydrate)")
    class RehydrateTests {

        @Test
        @DisplayName("Khôi phục đối tượng từ dữ liệu persistence đúng từng trường")
        void shouldRehydrateUser() {
            UUID id = UUID.randomUUID();
            UserAggregate user = UserAggregate.rehydrate(
                    id,
                    "charlie@example.com",
                    "hash",
                    "Charlie",
                    AccountStatus.SUSPENDED,
                    5L,
                    Set.of("USER", "ADMIN")
            );

            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getEmail()).isEqualTo("charlie@example.com");
            assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(user.getAuthVersion()).isEqualTo(5L);
            assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "ADMIN");
        }
    }

    @Nested
    @DisplayName("Thay đổi trạng thái (changeStatus)")
    class ChangeStatusTests {

        @Test
        @DisplayName("Đổi trạng thái mới thành công và tăng authVersion")
        void shouldChangeStatusAndIncrementAuthVersion() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);
            assertThat(user.getAuthVersion()).isEqualTo(1L);

            boolean changed = user.changeStatus(AccountStatus.SUSPENDED);

            assertThat(changed).isTrue();
            assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(user.getAuthVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Không đổi trạng thái và không tăng authVersion nếu trạng thái trùng lặp")
        void shouldNotChangeIfStatusIsSame() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            boolean changed = user.changeStatus(AccountStatus.ACTIVE);

            assertThat(changed).isFalse();
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.getAuthVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Ném NullPointerException khi newStatus là null")
        void shouldThrowWhenStatusIsNull() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            assertThatThrownBy(() -> user.changeStatus(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Quản lý roles (grantRole, revokeRole, updateRoles)")
    class RoleTests {

        @Test
        @DisplayName("Gán role mới chuẩn hóa chữ hoa và tăng authVersion")
        void shouldGrantRole() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            boolean granted = user.grantRole(" manager ");

            assertThat(granted).isTrue();
            assertThat(user.getRoles()).containsExactlyInAnyOrder("USER", "MANAGER");
            assertThat(user.getAuthVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Gán role đã tồn tại trả về false và không tăng version")
        void shouldNotGrantExistingRole() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            boolean granted = user.grantRole("USER");

            assertThat(granted).isFalse();
            assertThat(user.getAuthVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Thu hồi role thành công và tăng authVersion")
        void shouldRevokeRole() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", Set.of("USER", "ADMIN"));

            boolean revoked = user.revokeRole("ADMIN");

            assertThat(revoked).isTrue();
            assertThat(user.getRoles()).containsExactly("USER");
            assertThat(user.getAuthVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Thu hồi role không có trong danh sách trả về false")
        void shouldReturnFalseWhenRevokingNonExistentRole() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", Set.of("USER", "ADMIN"));

            boolean revoked = user.revokeRole("UNKNOWN");

            assertThat(revoked).isFalse();
            assertThat(user.getAuthVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Ném IllegalArgumentException khi thu hồi role cuối cùng duy nhất")
        void shouldThrowWhenRevokingLastRole() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            assertThatThrownBy(() -> user.revokeRole("USER"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot revoke the last remaining role");
        }

        @Test
        @DisplayName("Cập nhật toàn bộ roles và tăng authVersion")
        void shouldUpdateRoles() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            boolean updated = user.updateRoles(Set.of("ADMIN", "MANAGER"));

            assertThat(updated).isTrue();
            assertThat(user.getRoles()).containsExactlyInAnyOrder("ADMIN", "MANAGER");
            assertThat(user.getAuthVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("Ném IllegalArgumentException khi updateRoles rỗng hoặc null")
        void shouldThrowWhenNewRolesEmptyOrNull() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            assertThatThrownBy(() -> user.updateRoles(Collections.emptySet()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("newRoles cannot be empty");

            assertThatThrownBy(() -> user.updateRoles(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Ném IllegalArgumentException khi role rỗng hoặc quá dài")
        void shouldThrowWhenRoleInvalid() {
            UserAggregate user = UserAggregate.create("a@b.com", "hash", "Name", null);

            assertThatThrownBy(() -> user.grantRole("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("role cannot be null or empty");

            assertThatThrownBy(() -> user.grantRole("A".repeat(45)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("role needs to be between 3 and 40 characters");
        }
    }
}
