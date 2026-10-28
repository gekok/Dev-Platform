package dev.platform.task.deletion;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.event.MemberAssignmentsCleanupRequested;
import dev.platform.contracts.event.ProjectDeletionRequested;

/** Persist Inbox plus a unique durable job before ACK; listener wiring remains disabled until implemented. */
public interface TaskDeletionHandler {

    void requestProjectCascade(EventEnvelope<ProjectDeletionRequested> event);

    void requestMemberAssignmentCleanup(EventEnvelope<MemberAssignmentsCleanupRequested> event);
}
