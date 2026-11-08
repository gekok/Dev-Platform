package dev.platform.user.api;

import dev.platform.user.api.dto.InternalAccountStatusResponse;
import dev.platform.user.application.port.in.GetAccountStatusPort;
import dev.platform.user.domain.exception.AuthenticationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalUserController {
  private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

  private final GetAccountStatusPort getAccountStatusPort;
  private final String configuredServiceToken;

  public InternalUserController(
      GetAccountStatusPort getAccountStatusPort,
      @Value("${dev-platform.security.internal-service-token:}") String configuredServiceToken) {
    this.getAccountStatusPort = getAccountStatusPort;
    this.configuredServiceToken = configuredServiceToken;
  }

  @GetMapping("/internal/users/{userId}")
  public ResponseEntity<InternalAccountStatusResponse> getAccountStatus(
      @PathVariable UUID userId,
      @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken) {
    requireServiceToken(serviceToken);
    String status = getAccountStatusPort.getAccountStatus(userId).name();
    return ResponseEntity.ok(new InternalAccountStatusResponse(status));
  }

  private void requireServiceToken(String suppliedToken) {
    if (configuredServiceToken == null || configuredServiceToken.isBlank()
        || suppliedToken == null || suppliedToken.isBlank()
        || !MessageDigest.isEqual(
            configuredServiceToken.getBytes(StandardCharsets.UTF_8),
            suppliedToken.getBytes(StandardCharsets.UTF_8))) {
      throw new AuthenticationException("Missing or invalid service token");
    }
  }
}
