package dev.platform.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BcryptPasswordAdapterTest {

  private BcryptPasswordAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BcryptPasswordAdapter();
  }

  @Test
  @DisplayName("encode() phải băm mật khẩu thành chuỗi BCrypt an toàn, không trùng plaintext")
  void shouldEncodeRawPasswordUsingBcrypt() {
    String raw = "mySecretPassword123";

    String hash = adapter.encode(raw);

    assertThat(hash).isNotBlank();
    assertThat(hash).isNotEqualTo(raw);
    assertThat(hash).startsWith("$2a$12$");
    assertThat(hash).hasSize(60);
  }

  @Test
  @DisplayName("matches() trả về true khi người dùng nhập đúng mật khẩu")
  void shouldMatchCorrectRawPasswordWithHash() {
    String raw = "mySecretPassword123";
    String hash = adapter.encode(raw);

    boolean isMatch = adapter.matches(raw, hash);

    assertThat(isMatch).isTrue();
  }

  @Test
  @DisplayName("matches() trả về false khi người dùng nhập sai mật khẩu")
  void shouldRejectIncorrectPassword() {
    String raw = "mySecretPassword123";
    String wrongPassword = "wrongPassword456";
    String hash = adapter.encode(raw);

    boolean isMatch = adapter.matches(wrongPassword, hash);

    assertThat(isMatch).isFalse();
  }

  @Test
  @DisplayName("Hai lần băm cùng 1 mật khẩu phải ra 2 chuỗi hash khác nhau (nhờ Salt ngẫu nhiên)")
  void shouldGenerateDifferentHashesForSamePasswordDueToSalting() {
    String raw = "mySecretPassword123";

    String hash1 = adapter.encode(raw);
    String hash2 = adapter.encode(raw);

    assertThat(hash1).isNotEqualTo(hash2);
    assertThat(adapter.matches(raw, hash1)).isTrue();
    assertThat(adapter.matches(raw, hash2)).isTrue();
  }

  @Test
  @DisplayName("Xử lý an toàn khi tham số bị null")
  void shouldHandleNullInputSafely() {
    String raw = "mySecretPassword123";
    String hash = adapter.encode(raw);

    assertThat(adapter.matches(null, hash)).isFalse();
    assertThat(adapter.matches(raw, null)).isFalse();

    assertThatThrownBy(() -> adapter.encode(null))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Chấp nhận mật khẩu ở ngưỡng giới hạn 72 byte UTF-8 của BCrypt")
  void shouldAcceptPasswordsAtBcryptUtf8ByteLimit() {
    String raw = "a".repeat(72);

    String hash = adapter.encode(raw);

    assertThat(adapter.matches(raw, hash)).isTrue();
  }

  @Test
  @DisplayName("Từ chối mật khẩu vượt quá 72 byte UTF-8")
  void shouldRejectPasswordsOverBcryptUtf8ByteLimit() {
    String validHash = adapter.encode("valid-password");
    String oversizedPassword = "a".repeat(73);

    assertThatThrownBy(() -> adapter.encode(oversizedPassword))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Password must not exceed 72 UTF-8 bytes");
    assertThat(adapter.matches(oversizedPassword, validHash)).isFalse();
  }
}
