package dev.platform.notification.infrastructure.event;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;

import dev.platform.contracts.EventTypes;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "dev-platform.messaging.enabled",
        havingValue = "true")
@ConditionalOnProperty(
        name = "dev-platform.messaging.consumers.enabled",
        havingValue = "true")
public class NotificationRabbitConfiguration {

    public static final String LISTENER_CONTAINER_FACTORY =
        "notificationTaskEventListenerContainerFactory";

    public static final String TASK_EVENTS_QUEUE =
            "notification-service.task-events";

    public static final String DEAD_LETTER_EXCHANGE =
            "dev-platform.dead-letter";

    public static final String DEAD_LETTER_QUEUE =
            "notification-service.task-events.dlq";

    public static final String DEAD_LETTER_ROUTING_KEY =
            "notification-service.task-events";

    @Bean(name = LISTENER_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory notificationTaskEventListenerContainerFactory(
        ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory =
            new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);

        factory.setAdviceChain(
            RetryInterceptorBuilder.stateless()
                    .maxRetries(3)
                    .backOffOptions(5000L, 1.0, 5000L)
                    .recoverer(new RejectAndDontRequeueRecoverer())
                    .build());

        return factory;
    }
    @Bean
    Declarables notificationTaskEventTopology(
            TopicExchange domainEventsExchange) {
        Queue taskEventsQueue = QueueBuilder
                .durable(TASK_EVENTS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();

        DirectExchange deadLetterExchange =
                new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);

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