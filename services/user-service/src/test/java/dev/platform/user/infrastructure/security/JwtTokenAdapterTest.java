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
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class JwtTokenAdapterTest {

  @TempDir
  Path temporaryDirectory;

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

  @Test
  @DisplayName("Từ chối khóa RSA ngắn hơn 2048 bit")
  void shouldRejectWeakRsaKeyPair() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(1024);
    KeyPair weakPair = gen.generateKeyPair();

    assertThatThrownBy(() -> new JwtTokenAdapter(weakPair, 3600000L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 2048 bits");
  }

  @Test
  @DisplayName("Từ chối thời hạn token không dương")
  void shouldRejectNonPositiveExpiration() throws Exception {
    KeyPair keyPair = generateKeyPair();

    assertThatThrownBy(() -> new JwtTokenAdapter(keyPair, 0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expirationMillis must be positive");
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  @DisplayName("Fail-fast khi không thể lưu cặp khóa RSA đã sinh")
  void shouldFailFastWhenGeneratedKeyCannotBePersisted() {
    assertThatThrownBy(
            () -> JwtTokenAdapter.resolveKeyPair("", "", "", Path.of("/proc/dev-platform-jwt-test.key")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to read/write RSA key file");
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  @DisplayName("Khóa RSA tự sinh chỉ cho phép owner đọc và ghi")
  void shouldRestrictGeneratedKeyFilePermissions() throws Exception {
    Path keyFile = temporaryDirectory.resolve("jwt.key");

    JwtTokenAdapter.resolveKeyPair("", "", "", keyFile);

    assertThat(Files.getPosixFilePermissions(keyFile))
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  }

  @Test
  @DisplayName("Parse thành công cặp khóa PEM chứa ký tự newline dạng escape (\\n) từ biến môi trường")
  void shouldParsePemWithEscapedNewlines() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair keyPair = gen.generateKeyPair();

    String privBase64 = java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    String pubBase64 = java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

    String privWithEscapedNewlines = "-----BEGIN PRIVATE KEY-----\\n" + privBase64 + "\\n-----END PRIVATE KEY-----";
    String pubWithEscapedNewlines = "-----BEGIN PUBLIC KEY-----\\n" + pubBase64 + "\\n-----END PUBLIC KEY-----";

    JwtTokenAdapter envAdapter = new JwtTokenAdapter(3600000L, privWithEscapedNewlines, pubWithEscapedNewlines, "");
    UserAggregate user = UserAggregate.create("env@demo.local", "hash", "Env User", Set.of("USER"));

    String token = envAdapter.issue(user);
    assertThat(token).isNotBlank();
    assertThat(envAdapter.parseAndValidate(token)).isEqualTo(user.getId());
  }
}
