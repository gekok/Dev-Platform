package dev.platform.contracts;

public final class EventTypes {

    public static final String ACCOUNT_DELETION_REQUESTED = "account.deletion.requested";
    public static final String PROJECT_DELETION_REQUESTED = "project.deletion.requested";
    public static final String PROJECT_TASK_DATA_DELETED = "project.task-data.deleted";
    public static final String PROJECT_NOTIFICATIONS_DELETED = "project.notifications.deleted";
    public static final String MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED = "member.assignments.cleanup-requested";
    public static final String MEMBER_ASSIGNMENTS_CLEANED = "member.assignments.cleaned";
    public static final String PROJECT_CLEANUP_COMPLETED = "project.cleanup.completed";
    public static final String ACCOUNT_PROJECT_CLEANUP_COMPLETED = "account.project-cleanup.completed";

    private EventTypes() {
    }
}
