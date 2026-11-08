package dev.platform.user.api;

import dev.platform.user.api.dto.AdminCreateAccountRequest;
import dev.platform.user.api.dto.ChangeStatusRequest;
import dev.platform.user.api.dto.RoleRequest;
import dev.platform.user.api.dto.UpdateUserRolesRequest;
import dev.platform.user.api.dto.UserResponse;
import dev.platform.user.application.command.AdminCreateAccountCommand;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.AdminCreateAccountPort;
import dev.platform.user.application.port.in.ChangeUserStatusPort;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.ManageSystemRolePort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.exception.AccessDeniedException;
import dev.platform.user.domain.exception.AuthenticationException;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminCreateAccountPort adminCreateAccountPort;
  private final ChangeUserStatusPort changeUserStatusPort;
  private final ManageSystemRolePort manageSystemRolePort;
  private final GetCurrentUserPort getCurrentUserPort;
  private final TokenPort tokenPort;

  @PostMapping("/accounts")
  public ResponseEntity<UserResponse> createAccount(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody AdminCreateAccountRequest req) {
    verifySystemAdmin(authorization);

    AdminCreateAccountCommand cmd = new AdminCreateAccountCommand(
        req.email(),
        req.password(),
        req.fullName()
    );

    UserSummaryDto summary = adminCreateAccountPort.createAccount(cmd);
    return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(summary));
  }

  @PatchMapping("/users/{userId}/status")
  public ResponseEntity<Void> changeStatus(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable("userId") UUID userId,
      @Valid @RequestBody ChangeStatusRequest req) {
    verifySystemAdmin(authorization);

    changeUserStatusPort.changeStatus(userId, req.status());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{userId}/roles")
  public ResponseEntity<Void> grantRole(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable("userId") UUID userId,
      @Valid @RequestBody RoleRequest req) {
    verifySystemAdmin(authorization);

    manageSystemRolePort.grantRole(userId, req.role());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/users/{userId}/roles")
  public ResponseEntity<Void> updateRoles(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable("userId") UUID userId,
      @Valid @RequestBody UpdateUserRolesRequest req) {
    verifySystemAdmin(authorization);

    manageSystemRolePort.updateRoles(userId, req.roles());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/users/{userId}/roles/{roleCode}")
  public ResponseEntity<Void> revokeRole(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable("userId") UUID userId,
      @PathVariable("roleCode") String roleCode) {
    verifySystemAdmin(authorization);

    manageSystemRolePort.revokeRole(userId, roleCode);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/users/{userId}/roles")
  public ResponseEntity<Set<String>> getRoles(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable("userId") UUID userId) {
    verifySystemAdmin(authorization);

    return ResponseEntity.ok(manageSystemRolePort.getRoles(userId));
  }

  private void verifySystemAdmin(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new AuthenticationException("Missing or invalid Authorization header");
    }
    String token = authorization.substring(7).trim();
    if (token.isEmpty()) {
      throw new AuthenticationException("Missing or invalid Authorization header");
    }
    UUID callerId = tokenPort.parseAndValidate(token);

    UserSummaryDto caller = getCurrentUserPort.getCurrentUser(callerId);
    if (!caller.roles().contains("SYSTEM_ADMIN")) {
      throw new AccessDeniedException("SYSTEM_ADMIN role is required for this operation");
    }
  }
}
