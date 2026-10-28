package dev.platform.notification.deletion;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.event.ProjectDeletionRequested;

/** Persist Inbox plus a unique durable job before ACK; listener and batch worker remain TODO. */
public interface NotificationDeletionHandler {

    void requestCleanup(EventEnvelope<ProjectDeletionRequested> event);
}
