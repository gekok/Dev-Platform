package dev.platform.user.application.service;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.AccountProjectCleanupCompleted;
import dev.platform.shared.messaging.InboxEventRecorder;
import dev.platform.user.application.port.out.AccountDeletionStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionCompletionHandler {

    private static final String CONSUMER_NAME = "user-service.account-deletion-completion";

    private final InboxEventRecorder inboxEventRecorder;
    private final AccountDeletionStorePort store;

    @Transactional
    public void handle(EventEnvelope<AccountProjectCleanupCompleted> event) {
        if (!EventTypes.ACCOUNT_PROJECT_CLEANUP_COMPLETED.equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected event type: " + event.eventType());
        }
        if (!inboxEventRecorder.recordIfNew(CONSUMER_NAME, event.eventId(), event.eventType())) {
            return;
        }
        store.complete(event.payload().operationId(), event.payload().accountId());
    }
}
