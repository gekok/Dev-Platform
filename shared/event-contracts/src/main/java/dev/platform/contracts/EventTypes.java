package dev.platform.contracts;

public final class EventTypes {

    public static final String USER_STATUS_CHANGED = "user.status.changed";
    public static final String USER_SYSTEM_ROLES_CHANGED = "user.system-roles.changed";
    public static final String MEMBERSHIP_CHANGED = "project.membership.changed";
    public static final String MEMBERSHIP_REMOVED = "project.membership.removed";
    public static final String MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED = "member.assignments.cleanup-requested";
    public static final String MEMBER_ASSIGNMENTS_CLEANED = "member.assignments.cleaned";
    public static final String PROJECT_STATUS_CHANGED = "project.status.changed";
    public static final String MEMBER_ADDED = "project.member.added";
    public static final String MEMBER_REMOVED = "project.member.removed";
    public static final String OWNER_TRANSFERRED = "project.owner.transferred";

    private EventTypes() {
    }
}
