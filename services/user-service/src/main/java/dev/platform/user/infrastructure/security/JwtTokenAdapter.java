package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import dev.platform.user.application.port.out.TokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Adapter cấp phát JWT token sử dụng cặp khóa bất đối xứng RSA256 (Asymmetric Key Pair).
 * Private Key dùng để ký token (giữ bí mật tại user-service).
 * Public Key dùng để kiểm tra tính hợp lệ của token (chia sẻ cho Gateway / consumers).
 */
@Component
public class JwtTokenAdapter implements TokenPort {

    private final Algorithm algorithm;
    private final long expirationMillis;
    private final RSAPublicKey publicKey;

    public JwtTokenAdapter(
            @Value("${app.jwt.expiration-ms:3600000}") long expirationMillis) {
        this(generateRsaKeyPair(), expirationMillis);
    }

    public JwtTokenAdapter(KeyPair keyPair, long expirationMillis) {
        Objects.requireNonNull(keyPair, "keyPair cannot be null");
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.algorithm = Algorithm.RSA256(this.publicKey, privateKey);
        this.expirationMillis = expirationMillis;
    }

    @Override
    public String issue(UUID userId, String email) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMillis);

        return JWT.create()
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize RSA key pair generator", e);
        }
    }
}
