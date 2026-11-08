package dev.platform.user.domain.exception;

public class ProjectMembershipRequiredException extends RuntimeException {
    public ProjectMembershipRequiredException() {
        super("Project membership is required to view profiles");
    }
}
