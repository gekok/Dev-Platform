package dev.platform.user.application.port.in;

import java.util.Set;
import java.util.UUID;

public interface ManageSystemRolePort {
  void grantRole(UUID id, String role);

  void revokeRole(UUID id, String role);

  void updateRoles(UUID id, Set<String> role);

  Set<String> getRoles(UUID id);
}
