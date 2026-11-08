package dev.platform.notification.infrastructure.event;

import java.io.IOException;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.platform.contracts.EventEnvelope;
import dev.platform.notification.application.command.ProcessTaskNotificationCommand;
import dev.platform.notification.application.port.in.ProcessTaskNotificationPort;
import dev.platform.notification.application.port.in.ProcessTaskNotificationPort.ProcessResult;
import dev.platform.shared.messaging.MessagingSupportConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "dev-platform.messaging.enabled",
        havingValue = "true")
@ConditionalOnProperty(
        name = "dev-platform.messaging.consumers.enabled",
        havingValue = "true")
public class TaskEventListener {

    private final ObjectMapper objectMapper;
    private final TaskNotificationEventMapper eventMapper;
    private final ProcessTaskNotificationPort eventProcessor;

    @RabbitListener(
            queues = NotificationRabbitConfiguration.TASK_EVENTS_QUEUE,
            containerFactory = MessagingSupportConfiguration.CONSUMER_LISTENER_CONTAINER_FACTORY)
    public void onTaskEvent(Message message) {
        EventEnvelope<JsonNode> event;
        ProcessTaskNotificationCommand command;
        try {
            event = objectMapper.readValue(
                    message.getBody(),
                    new TypeReference<EventEnvelope<JsonNode>>() {
                    });

            command = eventMapper.toCommand(event);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Rejecting invalid task event message {} without requeue: {}",
                    message.getMessageProperties().getMessageId(), exception.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid task event contract", exception);
        }

        ProcessResult result;
        try {
            result = eventProcessor.process(command);
        } catch (IllegalArgumentException exception) {
            log.warn("Rejecting invalid task event eventId={} without requeue: {}",
                    event.eventId(), exception.getMessage());
            throw new AmqpRejectAndDontRequeueException(
                    "Invalid task event contract", exception);
        }

        log.info(
                "Processed task event eventId={} eventType={} result={}",
                event.eventId(),
                event.eventType(),
                result);
    }
}
