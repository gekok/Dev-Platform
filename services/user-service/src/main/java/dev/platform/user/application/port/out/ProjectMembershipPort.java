package dev.platform.user.application.port.out;

public interface ProjectMembershipPort {
    boolean isActiveProjectMember(long projectId, String authorization);
}
