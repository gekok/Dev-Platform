package dev.platform.shared.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.MembershipRemoved;
import dev.platform.shared.messaging.persistence.entity.OutboxEventEntity;
import dev.platform.shared.messaging.persistence.entity.OutboxStatus;
import dev.platform.shared.messaging.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventWriterTest {

    @Test
    void savesSerializedEnvelopeWithOriginalIdentityAndVersion() throws Exception {
        var repository = mock(OutboxEventJpaRepository.class);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var writer = new OutboxEventWriter(repository, mapper);
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var occurredAt = OffsetDateTime.parse("2026-01-02T03:04:05Z");
        var event = new EventEnvelope<>(eventId, EventTypes.MEMBERSHIP_REMOVED, 1,
                "PROJECT_MEMBERSHIP", "42:" + userId, 7L, occurredAt,
                new MembershipRemoved(42L, userId, 3L));

        writer.append(event);

        var captured = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captured.capture());
        OutboxEventEntity row = captured.getValue();
        JsonNode json = mapper.readTree(row.getPayload());
        assertEquals(eventId, row.getEventId());
        assertEquals(OutboxStatus.PENDING, row.getStatus());
        assertEquals(0, row.getAttemptCount());
        assertEquals(eventId.toString(), json.path("eventId").asText());
        assertEquals(EventTypes.MEMBERSHIP_REMOVED, json.path("eventType").asText());
        assertEquals(1, json.path("schemaVersion").asInt());
        assertEquals("PROJECT_MEMBERSHIP", json.path("aggregateType").asText());
        assertEquals("42:" + userId, json.path("aggregateId").asText());
        assertEquals(7L, json.path("aggregateVersion").asLong());
        assertEquals(occurredAt, mapper.treeToValue(json.path("occurredAt"), OffsetDateTime.class));
        assertEquals(42L, json.path("payload").path("projectId").asLong());
        assertEquals(userId.toString(), json.path("payload").path("userId").asText());
        assertEquals(3L, json.path("payload").path("membershipVersion").asLong());
    }
}
