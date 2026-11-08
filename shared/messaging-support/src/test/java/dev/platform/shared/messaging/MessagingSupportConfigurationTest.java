package dev.platform.shared.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.test.util.ReflectionTestUtils;

class MessagingSupportConfigurationTest {

    private final MessagingSupportConfiguration configuration =
            new MessagingSupportConfiguration();

    @Test
    void consumerRetryPolicyRetriesTransientErrorsThreeTimesAtFixedFiveSeconds() {
        RetryPolicy policy = configuration.consumerRetryPolicy();

        assertTrue(policy.shouldRetry(new IllegalStateException("temporary failure")));
        assertFalse(policy.shouldRetry(
                new AmqpRejectAndDontRequeueException("invalid contract")));
        assertFalse(policy.shouldRetry(new RuntimeException(
                "listener rejected message",
                new AmqpRejectAndDontRequeueException("invalid contract"))));

        BackOffExecution execution = policy.getBackOff().start();
        assertEquals(5_000L, execution.nextBackOff());
        assertEquals(5_000L, execution.nextBackOff());
        assertEquals(5_000L, execution.nextBackOff());
        assertEquals(BackOffExecution.STOP, execution.nextBackOff());
    }

    @Test
    void sharedListenerFactoryUsesAutoAckAndOneConsumer() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        SimpleRabbitListenerContainerFactory factory =
                configuration.sharedRabbitListenerContainerFactory(
                        connectionFactory,
                        configuration.consumerRetryPolicy());

        SimpleMessageListenerContainer container =
                factory.createListenerContainer(null);

        assertEquals(AcknowledgeMode.AUTO, container.getAcknowledgeMode());
        assertEquals(1, ReflectionTestUtils.getField(container, "concurrentConsumers"));
        assertEquals(1, ReflectionTestUtils.getField(container, "maxConcurrentConsumers"));
        assertNotNull(ReflectionTestUtils.getField(container, "adviceChain"));
    }
}
