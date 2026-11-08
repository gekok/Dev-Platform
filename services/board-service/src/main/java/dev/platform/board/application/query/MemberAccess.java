package dev.platform.board.application.query;

public record MemberAccess(boolean active, String role, long membershipVersion, String projectStatus) {
}
