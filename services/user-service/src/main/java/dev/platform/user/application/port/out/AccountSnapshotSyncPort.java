package dev.platform.user.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface AccountSnapshotSyncPort {
  void recordStatusChange(UUID id, String status, Set<String> roles, long authVersion);

  void recordSystemRoleChange(UUID id, String status, Set<String> roles, long authVersion);
}
