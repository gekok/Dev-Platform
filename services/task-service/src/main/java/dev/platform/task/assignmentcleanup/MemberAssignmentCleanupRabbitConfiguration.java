package dev.platform.task.assignmentcleanup;

import dev.platform.contracts.EventTypes;
import dev.platform.shared.messaging.MessagingSupportConfiguration;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = {
                "dev-platform.messaging.enabled",
                "dev-platform.messaging.consumers.enabled",
                "dev-platform.messaging.consumers.member-assignment-cleanup.retry-dlq-ready"
        },
        havingValue = "true")
public class MemberAssignmentCleanupRabbitConfiguration {

    @Bean
    Declarables memberAssignmentCleanupTopology(
            @Value("${dev-platform.messaging.consumers.member-assignment-cleanup.queue:task-service.member-assignment-cleanup}")
            String queueName,
            @Value("${dev-platform.messaging.consumers.member-assignment-cleanup.dead-letter-queue:task-service.member-assignment-cleanup.dlq}")
            String deadLetterQueueName,
            TopicExchange domainEventsExchange,
            DirectExchange deadLetterExchange) {
        Queue cleanupQueue = QueueBuilder.durable(queueName)
                .deadLetterExchange(deadLetterExchange.getName())
                .deadLetterRoutingKey(queueName)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();

        return new Declarables(
                cleanupQueue,
                deadLetterQueue,
                BindingBuilder.bind(cleanupQueue)
                        .to(domainEventsExchange)
                        .with(EventTypes.MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED),
                BindingBuilder.bind(deadLetterQueue)
                        .to(deadLetterExchange)
                        .with(queueName));
    }
}
