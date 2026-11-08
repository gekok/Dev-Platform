package dev.platform.user.application.port.out;

import java.util.UUID;

public interface ProjectMembershipPort {
    boolean isActiveProjectMember(long projectId, UUID userId);
}
