package dev.platform.user.api;

import dev.platform.user.api.dto.UserCandidateResponse;
import dev.platform.user.api.dto.UserProfileResponse;
import dev.platform.user.api.dto.UserResponse;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.UserDirectoryPort;
import dev.platform.user.application.port.out.ProjectMembershipPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.exception.ProjectMembershipRequiredException;
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

  private static final int MAX_DIRECTORY_LIMIT = 100;
  private static final int MAX_PROFILE_IDS = 100;

  private final GetCurrentUserPort getCurrentUserPort;
  private final UserDirectoryPort userDirectoryPort;
  private final ProjectMembershipPort projectMembershipPort;
  private final TokenPort tokenPort;

  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    UUID userId = verifyAuthenticated(authorization);
    UserSummaryDto summary = getCurrentUserPort.getCurrentUser(userId);
    return ResponseEntity.ok(UserResponse.from(summary));
  }

  @GetMapping
  public ResponseEntity<List<UserCandidateResponse>> searchUsers(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
    requireActiveAuthenticated(authorization);
    if (limit < 1 || limit > MAX_DIRECTORY_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_DIRECTORY_LIMIT);
    }
    List<UserCandidateResponse> users = userDirectoryPort.searchUsers(query, limit).stream()
        .map(UserCandidateResponse::from)
        .toList();
    return ResponseEntity.ok(users);
  }

  @GetMapping("/profiles")
  public ResponseEntity<List<UserProfileResponse>> getProfiles(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(value = "projectId", required = false) Long projectId,
      @RequestParam(value = "ids", required = false) Set<UUID> ids) {
    requireActiveAuthenticated(authorization);
    if (projectId == null || projectId <= 0) {
      throw new IllegalArgumentException("projectId must be a positive number");
    }
    if (ids != null && ids.size() > MAX_PROFILE_IDS) {
      throw new IllegalArgumentException("ids must contain no more than " + MAX_PROFILE_IDS + " values");
    }
    if (!projectMembershipPort.isActiveProjectMember(projectId, authorization)) {
      throw new ProjectMembershipRequiredException();
    }
    if (ids == null || ids.isEmpty()) {
      return ResponseEntity.ok(List.of());
    }
    List<UserProfileResponse> profiles = userDirectoryPort.getProfiles(ids).stream()
        .map(UserProfileResponse::from)
        .toList();
    return ResponseEntity.ok(profiles);
  }

  private UUID verifyAuthenticated(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new AuthenticationException("Missing or invalid Authorization header");
    }
    String token = authorization.substring(7).trim();
    if (token.isEmpty()) {
      throw new AuthenticationException("Missing or invalid Authorization header");
    }
    return tokenPort.parseAndValidate(token);
  }

  private UUID requireActiveAuthenticated(String authorization) {
    UUID userId = verifyAuthenticated(authorization);
    getCurrentUserPort.getCurrentUser(userId);
    return userId;
  }
}
