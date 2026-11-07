package dev.platform.user.application.port.in;

import java.util.Set;
import java.util.UUID;

public interface ManageSystemRolePort {
  void grantRole(UUID id, String roleCode);

  void revokeRole(UUID id, String roleCode);

  void updateRoles(UUID id, Set<String> roles);

  Set<String> getRoles(UUID id);
}
