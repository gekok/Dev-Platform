package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import dev.platform.user.domain.exception.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("parseAndValidate() trích xuất chính xác userId từ token hợp lệ")
    void shouldParseAndValidateValidToken() {
        UUID userId = UUID.randomUUID();
        String token = adapter.issue(userId, "user@example.com");

        UUID parsedId = adapter.parseAndValidate(token);

        assertThat(parsedId).isEqualTo(userId);
    }

    @Test
    @DisplayName("parseAndValidate() từ chối khi token bị null hoặc rỗng")
    void shouldThrowExceptionWhenTokenIsBlankOrNull() {
        assertThatThrownBy(() -> adapter.parseAndValidate(null))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Token cannot be blank");

        assertThatThrownBy(() -> adapter.parseAndValidate("   "))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Token cannot be blank");
    }

    @Test
    @DisplayName("parseAndValidate() từ chối khi chữ ký token không hợp lệ")
    void shouldThrowExceptionWhenSignatureIsInvalid() {
        UUID userId = UUID.randomUUID();
        String token = adapter.issue(userId, "user@example.com");
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> adapter.parseAndValidate(tamperedToken))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid or expired token");
    }

    @Test
    @DisplayName("parseAndValidate() từ chối khi token đã hết hạn")
    void shouldThrowExceptionWhenTokenIsExpired() {
        // Khởi tạo adapter với thời gian sống âm (đã hết hạn)
        JwtTokenAdapter expiredAdapter = new JwtTokenAdapter(-1000L);
        UUID userId = UUID.randomUUID();
        String expiredToken = expiredAdapter.issue(userId, "expired@example.com");

        assertThatThrownBy(() -> expiredAdapter.parseAndValidate(expiredToken))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("The Token has expired");
    }

    @Test
    @DisplayName("getPublicKey() trả về khóa công khai RSA hợp lệ")
    void shouldReturnValidRsaPublicKey() {
        assertThat(adapter.getPublicKey()).isNotNull();
        assertThat(adapter.getPublicKey().getAlgorithm()).isEqualTo("RSA");
    }
}
