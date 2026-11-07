package dev.platform.user.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BcryptPasswordAdapterTest {

    private BcryptPasswordAdapter adapter;

    // Chạy trước mỗi bài test để tạo mới adapter sạch sẽ
    @BeforeEach
    void setUp() {
        adapter = new BcryptPasswordAdapter();
    }

    @Test
    @DisplayName("encode() phải băm mật khẩu thành chuỗi BCrypt an toàn, không trùng plaintext")
    void shouldEncodeRawPasswordUsingBcrypt() {
        String raw = "mySecretPassword123";

        String hash = adapter.encode(raw);

        // Khẳng định: Chuỗi hash không được rỗng và không được giống chuỗi gốc ban đầu
        assertThat(hash).isNotBlank();
        assertThat(hash).isNotEqualTo(raw);
        // Chuẩn BCrypt luôn bắt đầu bằng ký tự $2a$ (hoặc $2b$) và dài 60 ký tự
        assertThat(hash).startsWith("$2a$");
        assertThat(hash).hasSize(60);
    }

    @Test
    @DisplayName("matches() trả về true khi người dùng nhập đúng mật khẩu")
    void shouldMatchCorrectRawPasswordWithHash() {
        String raw = "mySecretPassword123";
        String hash = adapter.encode(raw);

        boolean isMatch = adapter.matches(raw, hash);

        // Khẳng định: Nhập đúng mật khẩu thì phải trả về true
        assertThat(isMatch).isTrue();
    }

    @Test
    @DisplayName("matches() trả về false khi người dùng nhập sai mật khẩu")
    void shouldRejectIncorrectPassword() {
        String raw = "mySecretPassword123";
        String wrongPassword = "wrongPassword456";
        String hash = adapter.encode(raw);

        boolean isMatch = adapter.matches(wrongPassword, hash);

        // Khẳng định: Nhập sai mật khẩu thì phải trả về false
        assertThat(isMatch).isFalse();
    }

    @Test
    @DisplayName("Hai lần băm cùng 1 mật khẩu phải ra 2 chuỗi hash khác nhau (nhờ Salt ngẫu nhiên)")
    void shouldGenerateDifferentHashesForSamePasswordDueToSalting() {
        String raw = "mySecretPassword123";

        String hash1 = adapter.encode(raw);
        String hash2 = adapter.encode(raw);

        // Hai chuỗi băm phải khác nhau hoàn toàn
        assertThat(hash1).isNotEqualTo(hash2);
        // Nhưng khi so khớp mật khẩu gốc thì cả 2 đều phải đúng
        assertThat(adapter.matches(raw, hash1)).isTrue();
        assertThat(adapter.matches(raw, hash2)).isTrue();
    }

    @Test
    @DisplayName("Xử lý an toàn khi tham số bị null")
    void shouldHandleNullInputSafely() {
        String raw = "mySecretPassword123";
        String hash = adapter.encode(raw);

        // Mật khẩu null hoặc hash null thì đều trả về false
        assertThat(adapter.matches(null, hash)).isFalse();
        assertThat(adapter.matches(raw, null)).isFalse();

        // Băm null thì phải ném ra Exception
        assertThatThrownBy(() -> adapter.encode(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldAcceptPasswordsAtBcryptUtf8ByteLimit() {
        String raw = "a".repeat(72);

        String hash = adapter.encode(raw);

        assertThat(adapter.matches(raw, hash)).isTrue();
    }

    @Test
    void shouldRejectPasswordsOverBcryptUtf8ByteLimit() {
        String validHash = adapter.encode("valid-password");
        String oversizedPassword = "a".repeat(73);

        assertThatThrownBy(() -> adapter.encode(oversizedPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must not exceed 72 UTF-8 bytes");
        assertThat(adapter.matches(oversizedPassword, validHash)).isFalse();
    }
}
