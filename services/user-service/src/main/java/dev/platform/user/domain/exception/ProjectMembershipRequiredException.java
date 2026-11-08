package dev.platform.user.domain.exception;

public class ProjectMembershipRequiredException extends RuntimeException {
  public ProjectMembershipRequiredException(String message) {
    super(message);
  }
}
