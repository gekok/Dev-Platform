package dev.platform.board.infrastructure.adapter;

import dev.platform.board.application.port.out.TaskCountPort;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpTaskCountAdapter implements TaskCountPort {
    private final RestClient restClient;
    private final String baseUrl;
    private final String serviceToken;

    public HttpTaskCountAdapter(
            RestClient.Builder builder,
            @Value("${dev-platform.internal.task-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${dev-platform.security.internal-service-token:}") String serviceToken) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(2_000);
        this.restClient = builder.requestFactory(requestFactory).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.serviceToken = serviceToken;
    }

    @Override
    public long countByColumnId(Long columnId) {
        if (serviceToken.isBlank()) {
            throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                    "Task Service verification is not configured.");
        }
        try {
            TaskCountResponse response = restClient.get()
                    .uri(baseUrl + "/internal/columns/{columnId}/task-count", columnId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(TaskCountResponse.class);
            if (response == null || response.count() < 0) {
                throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                        "Task Service returned an invalid task count.");
            }
            return response.count();
        } catch (RestClientException exception) {
            throw ProjectRuleViolation.unavailable("DEPENDENCY_UNAVAILABLE",
                    "Task Service is unavailable; Column was not deleted.");
        }
    }

    private record TaskCountResponse(long count) {
    }
}
