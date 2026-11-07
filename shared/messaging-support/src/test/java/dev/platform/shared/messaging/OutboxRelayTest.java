package dev.platform.shared.messaging;

import dev.platform.shared.messaging.persistence.entity.OutboxEventEntity;
import dev.platform.shared.messaging.persistence.entity.OutboxStatus;
import dev.platform.shared.messaging.persistence.repository.OutboxEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    @Test
    void retryAfterNackKeepsEventIdentityAndPayload() {
        var repository = mock(OutboxEventJpaRepository.class);
        var rabbit = mock(RabbitTemplate.class);
        var relay = new OutboxRelay(repository, rabbit);
        ReflectionTestUtils.setField(relay, "retryDelay", Duration.ofSeconds(5));
        ReflectionTestUtils.setField(relay, "confirmTimeout", Duration.ofSeconds(1));
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"aggregateVersion\":7}";
        var event = OutboxEventEntity.pending(eventId, "project.membership.removed", "PROJECT_MEMBERSHIP",
                "42:user", 7, payload, OffsetDateTime.now());
        when(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any())).thenReturn(List.of(event));
        List<String> published = new ArrayList<>();
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            published.add(message.getMessageProperties().getMessageId() + ":"
                    + new String(message.getBody(), StandardCharsets.UTF_8));
            correlation.getFuture().complete(new CorrelationData.Confirm(published.size() > 1, "nack"));
            return null;
        }).when(rabbit).send(eq(OutboxRelay.EXCHANGE), eq(event.getEventType()), any(Message.class),
                any(CorrelationData.class));

        relay.publishDueEvents();
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        relay.publishDueEvents();

        assertEquals(List.of(eventId + ":" + payload, eventId + ":" + payload), published);
        assertEquals(OutboxStatus.SENT, event.getStatus());
        assertNotNull(event.getSentAt());
        assertEquals(7, event.getAggregateVersion());
        verify(repository, times(2)).save(event);
    }
}
