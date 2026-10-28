package dev.platform.contracts;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Provisional event envelope shared by services. Payloads remain subject to contract review. */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        T payload) {
}
