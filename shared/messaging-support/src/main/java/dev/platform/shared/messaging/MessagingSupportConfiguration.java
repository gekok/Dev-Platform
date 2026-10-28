package dev.platform.shared.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Import({OutboxEventWriter.class, InboxEventRecorder.class, OutboxRelay.class})
public class MessagingSupportConfiguration {

    @Bean
    @ConditionalOnProperty(name = "dev-platform.messaging.enabled", havingValue = "true")
    TopicExchange domainEventsExchange() {
        return new TopicExchange(OutboxRelay.EXCHANGE, true, false);
    }
}
