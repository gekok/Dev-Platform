package dev.platform.shared.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.shared.messaging.persistence.entity.OutboxEventEntity;
import dev.platform.shared.messaging.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope<?> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            repository.save(OutboxEventEntity.pending(
                    event.eventId(),
                    event.eventType(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.aggregateVersion(),
                    payload,
                    OffsetDateTime.now()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize outbox event " + event.eventId(), exception);
        }
    }
}
