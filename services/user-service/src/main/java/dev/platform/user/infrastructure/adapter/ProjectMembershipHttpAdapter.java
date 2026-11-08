package dev.platform.user.infrastructure.adapter;

import dev.platform.user.application.port.out.ProjectMembershipPort;
import dev.platform.user.domain.exception.AuthorizationUnavailableException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class ProjectMembershipHttpAdapter implements ProjectMembershipPort {
    private final RestClient boardServiceClient;
    private final String serviceToken;

    public ProjectMembershipHttpAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.services.board-service.base-url:http://localhost:8082}") String boardServiceBaseUrl,
            @Value("${dev-platform.security.internal-service-token:}") String serviceToken) {
        this.boardServiceClient = restClientBuilder.baseUrl(boardServiceBaseUrl).build();
        this.serviceToken = serviceToken;
    }

    @Override
    public boolean isActiveProjectMember(long projectId, UUID userId) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new AuthorizationUnavailableException("Internal service token is not configured");
        }
        try {
            MembershipStatus response = boardServiceClient.get()
                    .uri("/internal/projects/{projectId}/members/{userId}", projectId, userId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(MembershipStatus.class);
            if (response == null || response.role() == null || response.status() == null
                    || response.projectStatus() == null) {
                throw new AuthorizationUnavailableException("Board returned an invalid Membership response");
            }
            return "ACTIVE".equals(response.status())
                    && "ACTIVE".equals(response.projectStatus())
                    && ("OWNER".equals(response.role()) || "MEMBER".equals(response.role()));
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (HttpClientErrorException.Forbidden exception) {
            log.warn("Board rejected internal Membership verification", exception);
            throw new AuthorizationUnavailableException("Could not verify Project access", exception);
        } catch (RestClientException exception) {
            log.warn("Could not verify caller Project Membership", exception);
            throw new AuthorizationUnavailableException("Could not verify Project access", exception);
        }
    }

    private record MembershipStatus(String role, String status, Long membershipVersion, String projectStatus) {}
}
