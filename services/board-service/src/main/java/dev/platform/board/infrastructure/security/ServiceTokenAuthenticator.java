package dev.platform.board.infrastructure.security;

import dev.platform.board.application.port.out.InternalServiceAuthenticationPort;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ServiceTokenAuthenticator implements InternalServiceAuthenticationPort {
    private final String configuredToken;

    public ServiceTokenAuthenticator(
            @Value("${dev-platform.security.internal-service-token:}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    public void verify(String serviceToken) {
        if (configuredToken.isBlank()) {
            throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                    "Internal service authentication is not configured.");
        }
        if (serviceToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw ProjectRuleViolation.unauthorized("Service authentication failed.");
        }
    }
}
