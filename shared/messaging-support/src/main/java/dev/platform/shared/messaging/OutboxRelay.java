package dev.platform.shared.messaging;

import dev.platform.shared.messaging.persistence.entity.OutboxEventEntity;
import dev.platform.shared.messaging.persistence.entity.OutboxStatus;
import dev.platform.shared.messaging.persistence.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-platform.messaging.enabled", havingValue = "true")
@ConditionalOnProperty(name = "dev-platform.messaging.outbox-relay.enabled", havingValue = "true")
public class OutboxRelay {

    public static final String EXCHANGE = "dev-platform.domain-events";

    private final OutboxEventJpaRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @org.springframework.beans.factory.annotation.Value("${dev-platform.messaging.outbox-relay.confirm-timeout:PT5S}")
    private Duration confirmTimeout;

    @org.springframework.beans.factory.annotation.Value("${dev-platform.messaging.outbox-relay.retry-delay:PT5S}")
    private Duration retryDelay;

    @Scheduled(fixedDelayString = "${dev-platform.messaging.outbox-relay.fixed-delay-ms:1000}")
    public void publishDueEvents() {
        var pending = repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                OffsetDateTime.now());
        pending.forEach(this::publishOne);
    }

    private void publishOne(OutboxEventEntity event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setMessageId(event.getEventId().toString());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

            CorrelationData correlation = new CorrelationData(event.getEventId().toString());
            Message message = new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);
            rabbitTemplate.send(EXCHANGE, event.getEventType(), message, correlation);

            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                String error = correlation.getReturned() == null
                        ? confirm.reason()
                        : "Unroutable event: " + correlation.getReturned().getReplyText();
                scheduleRetry(event, error);
                return;
            }
            event.markSent(OffsetDateTime.now());
            repository.save(event);
        } catch (Exception exception) {
            log.warn("Outbox publish failed for event {}: {}", event.getEventId(), exception.getMessage());
            scheduleRetry(event, exception.getMessage());
        }
    }

    private void scheduleRetry(OutboxEventEntity event, String reason) {
        event.scheduleRetry(OffsetDateTime.now().plus(retryDelay), reason == null ? "Publish failed" : reason);
        repository.save(event);
    }
}
