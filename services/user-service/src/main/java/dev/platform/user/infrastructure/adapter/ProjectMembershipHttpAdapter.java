package dev.platform.user.infrastructure.adapter;

import dev.platform.user.application.port.out.ProjectMembershipPort;
import dev.platform.user.domain.exception.AuthorizationUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ProjectMembershipHttpAdapter implements ProjectMembershipPort {
    private final RestClient boardServiceClient;

    public ProjectMembershipHttpAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.board-service.base-url:http://localhost:8082}") String boardServiceBaseUrl) {
        this.boardServiceClient = restClientBuilder.baseUrl(boardServiceBaseUrl).build();
    }

    @Override
    public boolean isActiveProjectMember(long projectId, String authorization) {
        try {
            // Board-service owner must implement this endpoint before profile reads can succeed.
            ResponseEntity<Void> response = boardServiceClient.get()
                    .uri("/internal/v1/projects/{projectId}/members/me", projectId)
                    .header("Authorization", authorization)
                    .retrieve()
                    .toBodilessEntity();
            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                return true;
            }
            throw new AuthorizationUnavailableException("Unexpected Membership response status");
        } catch (HttpClientErrorException.Forbidden exception) {
            return false;
        } catch (RestClientException exception) {
            log.warn("Could not verify caller Project Membership", exception);
            throw new AuthorizationUnavailableException("Could not verify Project access", exception);
        }
    }
}
