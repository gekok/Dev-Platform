package dev.platform.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
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
    assertThat(JWT.decode(token).getIssuer()).isEqualTo("dev-platform-user-service");
  }

  @Test
  @DisplayName("issue() uses the configured issuer")
  void shouldIssueTokenWithConfiguredIssuer() throws Exception {
    KeyPair keyPair = generateKeyPair();
    JwtTokenAdapter configuredAdapter =
        new JwtTokenAdapter(keyPair, 3600000L, "configured-user-service");
    UserAggregate user = UserAggregate.create("user@demo.local", "hashed", "Test User", Set.of("USER"));

    String token = configuredAdapter.issue(user);

    assertThat(JWT.decode(token).getIssuer()).isEqualTo("configured-user-service");
    assertThat(configuredAdapter.parseAndValidate(token)).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("parseAndValidate() rejects a token issued by a different service")
  void shouldRejectTokenWithWrongIssuer() throws Exception {
    KeyPair keyPair = generateKeyPair();
    JwtTokenAdapter validatingAdapter = new JwtTokenAdapter(keyPair, 3600000L);
    String token =
        JWT.create()
            .withIssuer("untrusted-issuer")
            .withSubject(UUID.randomUUID().toString())
            .sign(Algorithm.RSA256(
                (RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate()));

    assertThatThrownBy(() -> validatingAdapter.parseAndValidate(token))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Invalid or expired token");
  }

  private static KeyPair generateKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
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

  @Test
  @DisplayName("Fail-fast khi cấu hình thiếu một trong hai khóa PEM")
  void shouldFailFastWhenPemIncomplete() {
    assertThatThrownBy(() -> new JwtTokenAdapter(3600000L, "some-priv-key", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("both 'app.jwt.private-key' and 'app.jwt.public-key' must be provided");
  }

  @Test
  @DisplayName("Ném lỗi khi public key và private key không cùng một cặp")
  void shouldThrowWhenKeysDoNotMatch() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair1 = gen.generateKeyPair();
    KeyPair pair2 = gen.generateKeyPair();

    KeyPair mismatchedPair = new KeyPair(pair1.getPublic(), pair2.getPrivate());

    assertThatThrownBy(() -> new JwtTokenAdapter(mismatchedPair, 3600000L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("do not match");
  }
}
