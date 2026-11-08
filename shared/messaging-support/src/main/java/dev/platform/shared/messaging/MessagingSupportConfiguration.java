package dev.platform.shared.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Import({OutboxEventWriter.class, InboxEventRecorder.class, OutboxRelay.class})
public class MessagingSupportConfiguration {

    public static final String CONSUMER_LISTENER_CONTAINER_FACTORY =
            "sharedRabbitListenerContainerFactory";
    public static final String DEAD_LETTER_EXCHANGE = "dev-platform.dead-letter";

    @Bean
    @ConditionalOnProperty(name = "dev-platform.messaging.enabled", havingValue = "true")
    TopicExchange domainEventsExchange() {
        return new TopicExchange(OutboxRelay.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnProperty(
            name = {"dev-platform.messaging.enabled", "dev-platform.messaging.consumers.enabled"},
            havingValue = "true")
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnProperty(
            name = {"dev-platform.messaging.enabled", "dev-platform.messaging.consumers.enabled"},
            havingValue = "true")
    RetryPolicy consumerRetryPolicy() {
        return RetryPolicy.builder()
                .maxRetries(3)
                .delay(Duration.ofSeconds(5))
                .multiplier(1.0)
                .excludes(AmqpRejectAndDontRequeueException.class)
                .build();
    }

    @Bean(name = CONSUMER_LISTENER_CONTAINER_FACTORY)
    @ConditionalOnProperty(
            name = {"dev-platform.messaging.enabled", "dev-platform.messaging.consumers.enabled"},
            havingValue = "true")
    SimpleRabbitListenerContainerFactory sharedRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            RetryPolicy consumerRetryPolicy) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(consumerRetryPolicy)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
