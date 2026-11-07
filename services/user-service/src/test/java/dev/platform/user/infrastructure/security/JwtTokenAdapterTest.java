package dev.platform.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenAdapterTest {

  private final JwtTokenAdapter adapter = new JwtTokenAdapter(3600000L);

  @Test
  @DisplayName("Issue token JWT chứa đầy đủ claims và parse xác thực thành công")
  void shouldIssueAndValidateToken() {
    UserAggregate user = UserAggregate.create("user@demo.local", "hashed", "Test User", Set.of("USER", "SYSTEM_ADMIN"));

    String token = adapter.issue(user);

    assertThat(token).isNotBlank();
    UUID extractedUserId = adapter.parseAndValidate(token);
    assertThat(extractedUserId).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("Xuất Public Key định dạng PEM hợp lệ")
  void shouldExportPublicKeyPem() {
    String pem = adapter.getPublicKeyPem();

    assertThat(pem).isNotBlank();
    assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----");
    assertThat(pem).endsWith("-----END PUBLIC KEY-----");
  }

  @Test
  @DisplayName("Ném lỗi khi parse token rỗng hoặc không hợp lệ")
  void shouldThrowWhenTokenIsInvalid() {
    assertThatThrownBy(() -> adapter.parseAndValidate(null))
        .isInstanceOf(AuthenticationException.class);

    assertThatThrownBy(() -> adapter.parseAndValidate("invalid.token.structure"))
        .isInstanceOf(AuthenticationException.class);
  }
}
