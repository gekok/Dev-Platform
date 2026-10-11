package dev.platform.task.todo.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtCurrentUserIdResolver {

    private final Algorithm algorithm;

    public JwtCurrentUserIdResolver(@Value("${app.jwt.secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    public UUID resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization token khong hop le");
        }
        return UUID.fromString(JWT.require(algorithm).build()
                .verify(authorizationHeader.substring("Bearer ".length()).trim())
                .getSubject());
    }
}
