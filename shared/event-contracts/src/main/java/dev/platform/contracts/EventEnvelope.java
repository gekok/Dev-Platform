package dev.platform.contracts;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Shared event envelope; payload and version semantics are defined by each event contract. */
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
