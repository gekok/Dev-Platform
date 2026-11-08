package dev.platform.board.infrastructure.adapter;

import dev.platform.board.application.port.out.AccountLookupPort;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class HttpAccountLookupAdapter implements AccountLookupPort {
    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public HttpAccountLookupAdapter(
            RestClient.Builder builder,
            @Value("${dev-platform.internal.user-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${dev-platform.security.internal-service-token:}") String serviceToken) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    @Override
    public boolean isActive(UUID userId) {
        if (serviceToken.isBlank()) {
            throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                    "User Service verification is not configured.");
        }
        try {
            AccountStatus response = restClient.get()
                    .uri(baseUrl + "/internal/users/{userId}", userId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(AccountStatus.class);
            if (response == null || response.status() == null) {
                throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                        "User Service returned an invalid account response.");
            }
            return "ACTIVE".equals(response.status());
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RestClientException exception) {
            throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                    "User Service is unavailable.");
        }
    }

    private record AccountStatus(String status) {
    }
}
