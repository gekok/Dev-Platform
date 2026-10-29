package dev.platform.board.membership.assignmentcleanup;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.event.MemberAssignmentsCleaned;

/** Completion boundary for the independent Task assignment-cleanup job. */
public interface MemberAssignmentCleanupCompletionHandler {

    void onMemberAssignmentsCleaned(EventEnvelope<MemberAssignmentsCleaned> event);
}
