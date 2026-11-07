package dev.platform.user.api;

import dev.platform.user.api.dto.UserResponse;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.UserDirectoryPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.exception.AuthenticationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final GetCurrentUserPort getCurrentUserPort;
  private final UserDirectoryPort userDirectoryPort;
  private final TokenPort tokenPort;

  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new AuthenticationException("Missing or invalid Authorization header");
    }

    String token = authorization.substring(7).trim();
    UUID userId = tokenPort.parseAndValidate(token);

    UserSummaryDto summary = getCurrentUserPort.getCurrentUser(userId);
    return ResponseEntity.ok(UserResponse.from(summary));
  }

  @GetMapping
  public ResponseEntity<List<UserResponse>> searchUsers(
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
    List<UserResponse> users = userDirectoryPort.searchUsers(query, limit).stream()
        .map(UserResponse::from)
        .toList();
    return ResponseEntity.ok(users);
  }

  @GetMapping("/profiles")
  public ResponseEntity<List<UserResponse>> getProfiles(
      @RequestParam(value = "ids", required = false) Set<UUID> ids) {
    List<UserResponse> profiles = userDirectoryPort.getProfiles(ids).stream()
        .map(UserResponse::from)
        .toList();
    return ResponseEntity.ok(profiles);
  }
}
