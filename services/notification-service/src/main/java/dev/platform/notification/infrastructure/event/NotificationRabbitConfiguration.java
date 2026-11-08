package dev.platform.notification.infrastructure.event;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.DirectExchange;

import dev.platform.contracts.EventTypes;
import dev.platform.shared.messaging.MessagingSupportConfiguration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = {"dev-platform.messaging.enabled", "dev-platform.messaging.consumers.enabled"},
        havingValue = "true")
public class NotificationRabbitConfiguration {

    public static final String TASK_EVENTS_QUEUE =
            "notification-service.task-events";

    public static final String DEAD_LETTER_QUEUE =
            "notification-service.task-events.dlq";

    public static final String DEAD_LETTER_ROUTING_KEY =
            "notification-service.task-events";

    @Bean
    Declarables notificationTaskEventTopology(
            TopicExchange domainEventsExchange,
            DirectExchange deadLetterExchange) {
        Queue taskEventsQueue = QueueBuilder
                .durable(TASK_EVENTS_QUEUE)
                .deadLetterExchange(MessagingSupportConfiguration.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();

        Queue deadLetterQueue = QueueBuilder
                .durable(DEAD_LETTER_QUEUE)
                .build();

        return new Declarables(
                taskEventsQueue,
                deadLetterExchange,
                deadLetterQueue,

                BindingBuilder.bind(taskEventsQueue)
                        .to(domainEventsExchange)
                        .with(EventTypes.CARD_ASSIGNED),

                BindingBuilder.bind(taskEventsQueue)
                        .to(domainEventsExchange)
                        .with(EventTypes.COMMENT_MENTIONED),

                BindingBuilder.bind(deadLetterQueue)
                        .to(deadLetterExchange)
                        .with(DEAD_LETTER_ROUTING_KEY));
    }
}
