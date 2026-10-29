package dev.platform.task.assignmentcleanup;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.event.MemberAssignmentsCleanupRequested;

/** Persist Inbox, fence and durable cleanup job before a future listener ACK. */
public interface MemberAssignmentCleanupHandler {

    void requestMemberAssignmentCleanup(EventEnvelope<MemberAssignmentsCleanupRequested> event);
}
