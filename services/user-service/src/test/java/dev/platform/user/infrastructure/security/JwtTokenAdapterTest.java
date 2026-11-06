package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenAdapterTest {

    private JwtTokenAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JwtTokenAdapter(3600000L); // 1 giờ
    }

    @Test
    @DisplayName("issue() phát hành token JWT hợp lệ được ký bằng RSA256")
    void shouldIssueValidRsa256JwtToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        String token = adapter.issue(userId, email);

        assertThat(token).isNotBlank();
        // JWT gồm 3 phần ngăn cách bởi dấu chấm (Header.Payload.Signature)
        assertThat(token.split("\\.")).hasSize(3);

        // Xác thực token bằng chính Public Key RSA của adapter
        DecodedJWT decoded = JWT.require(Algorithm.RSA256(adapter.getPublicKey(), null))
                .build()
                .verify(token);

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaim("email").asString()).isEqualTo(email);
        assertThat(decoded.getExpiresAt()).isAfter(new Date());
    }

    @Test
    @DisplayName("issue() từ chối khi userId bị null")
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThatThrownBy(() -> adapter.issue(null, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId cannot be null");
    }

    @Test
    @DisplayName("getPublicKey() trả về khóa công khai RSA hợp lệ")
    void shouldReturnValidRsaPublicKey() {
        assertThat(adapter.getPublicKey()).isNotNull();
        assertThat(adapter.getPublicKey().getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    @DisplayName("getPublicKeyPem() xuất khóa công khai theo định dạng chuẩn PEM")
    void shouldReturnValidPublicKeyPemFormat() {
        String pem = adapter.getPublicKeyPem();
        assertThat(pem).isNotBlank();
        assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(pem).endsWith("-----END PUBLIC KEY-----");
    }

    @Test
    @DisplayName("Khóa ký ổn định qua các lần restart khi lưu trữ file")
    void shouldPreserveKeyAcrossRestartsWhenFileIsSpecified() throws Exception {
        Path tempKeyFile = Files.createTempFile("test-jwt-rsa", ".key");
        try {
            // Instance 1: Sinh và lưu khóa vào tempKeyFile
            JwtTokenAdapter instance1 = new JwtTokenAdapter(3600000L, "", "", tempKeyFile.toString());
            UUID userId = UUID.randomUUID();
            String token = instance1.issue(userId, "alex@example.com");

            // Instance 2 (giả lập restart): Nạp lại khóa từ tempKeyFile
            JwtTokenAdapter instance2 = new JwtTokenAdapter(3600000L, "", "", tempKeyFile.toString());

            // Token của instance 1 vẫn phải được xác minh thành công bởi instance 2
            DecodedJWT decoded = JWT.require(Algorithm.RSA256(instance2.getPublicKey(), null))
                    .build()
                    .verify(token);

            assertThat(decoded.getSubject()).isEqualTo(userId.toString());
            assertThat(decoded.getClaim("email").asString()).isEqualTo("alex@example.com");
        } finally {
            Files.deleteIfExists(tempKeyFile);
        }
    }
}
