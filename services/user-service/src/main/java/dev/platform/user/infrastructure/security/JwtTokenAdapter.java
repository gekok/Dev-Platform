package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import dev.platform.user.application.port.out.TokenPort;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenAdapter implements TokenPort {
    private final Algorithm algorithm;
    private final long expirationMillis;

    public JwtTokenAdapter(@Value("${app.jwt.secret:dev-platform-secret-key-must-be-at-least-256-bits-long}") String secret, @Value("${app.jwt.expiration-ms:3600000}") long expirationMillis)
            {
                this.algorithm = Algorithm.HMAC256(secret);
                this.expirationMillis = expirationMillis;
            }

    @Override
    public String issue(UUID userId, String email) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);

        return JWT.create()
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withIssuedAt(now)
                .withExpiresAt(expiration)
                .sign(algorithm);
    }
}
