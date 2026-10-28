package dev.platform.board.deletion;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.event.AccountDeletionRequested;
import dev.platform.contracts.event.MemberAssignmentsCleaned;
import dev.platform.contracts.event.ProjectNotificationsDeleted;
import dev.platform.contracts.event.ProjectTaskDataDeleted;

/** Application boundary; register an implementation only after the Project data adapter exists. */
public interface ProjectDeletionCoordinator {

    void startAccountDeletion(EventEnvelope<AccountDeletionRequested> event);

    void onTaskDataDeleted(EventEnvelope<ProjectTaskDataDeleted> event);

    void onNotificationsDeleted(EventEnvelope<ProjectNotificationsDeleted> event);

    void onMemberAssignmentsCleaned(EventEnvelope<MemberAssignmentsCleaned> event);
}
