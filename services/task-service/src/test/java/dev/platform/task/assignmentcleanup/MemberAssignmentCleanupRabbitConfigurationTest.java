package dev.platform.task.assignmentcleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import dev.platform.contracts.EventTypes;
import dev.platform.shared.messaging.MessagingSupportConfiguration;
import dev.platform.shared.messaging.OutboxRelay;

class MemberAssignmentCleanupRabbitConfigurationTest {

    @Test
    void declaresDurableQueueAndRoutesRejectedMessagesToServiceDlq() {
        Declarables topology = new MemberAssignmentCleanupRabbitConfiguration()
                .memberAssignmentCleanupTopology(
                        "task-service.member-assignment-cleanup",
                        "task-service.member-assignment-cleanup.dlq",
                        new TopicExchange(OutboxRelay.EXCHANGE, true, false),
                        new DirectExchange(
                                MessagingSupportConfiguration.DEAD_LETTER_EXCHANGE,
                                true,
                                false));

        Queue mainQueue = findQueue(topology, "task-service.member-assignment-cleanup");
        Queue deadLetterQueue = findQueue(topology, "task-service.member-assignment-cleanup.dlq");
        Binding eventBinding = findBinding(topology, EventTypes.MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED);
        Binding deadLetterBinding = findBinding(topology, "task-service.member-assignment-cleanup");

        assertNotNull(mainQueue);
        assertNotNull(deadLetterQueue);
        assertTrue(mainQueue.isDurable());
        assertTrue(deadLetterQueue.isDurable());
        assertEquals(
                MessagingSupportConfiguration.DEAD_LETTER_EXCHANGE,
                mainQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(EventTypes.MEMBER_ASSIGNMENTS_CLEANUP_REQUESTED, eventBinding.getRoutingKey());
        assertEquals("task-service.member-assignment-cleanup", deadLetterBinding.getRoutingKey());
    }

    private static Queue findQueue(Declarables declarables, String queueName) {
        return declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(queue -> queue.getName().equals(queueName))
                .findFirst()
                .orElseThrow();
    }

    private static Binding findBinding(Declarables declarables, String routingKey) {
        return declarables.getDeclarables().stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .filter(binding -> binding.getRoutingKey().equals(routingKey))
                .findFirst()
                .orElseThrow();
    }
}
