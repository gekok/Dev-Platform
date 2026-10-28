package dev.platform.shared.messaging;

import dev.platform.shared.messaging.persistence.entity.InboxEventEntity;
import dev.platform.shared.messaging.persistence.entity.InboxEventId;
import dev.platform.shared.messaging.persistence.repository.InboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InboxEventRecorder {

    private final InboxEventJpaRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordIfNew(String consumerName, UUID eventId, String eventType) {
        InboxEventId inboxEventId = new InboxEventId(consumerName, eventId);
        if (repository.existsById(inboxEventId)) {
            return false;
        }
        repository.save(new InboxEventEntity(consumerName, eventId, eventType, OffsetDateTime.now()));
        return true;
    }
}
