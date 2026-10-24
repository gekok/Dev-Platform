package dev.platform.user.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.application.port.out.TokenReaderPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenAdapter implements TokenPort, TokenReaderPort {

    private final Algorithm algorithm;
    private final long expirationMillis;

    public JwtTokenAdapter(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:3600000}") long expirationMillis) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationMillis = expirationMillis;
    }

    @Override
    public String issue(UUID userId, String email) {
        final Date issuedAt = new Date();
        final Date expiresAt = new Date(issuedAt.getTime() + expirationMillis);
        return JWT.create()
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withIssuedAt(issuedAt)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    @Override
    public UUID currentUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization token khong hop le");
        }
        final String token = authorizationHeader.substring("Bearer ".length()).trim();
        final DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
        return UUID.fromString(jwt.getSubject());
    }
}
