package dev.platform.notification.infrastructure.event;

import java.io.IOException;

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
            containerFactory =
                    NotificationRabbitConfiguration.LISTENER_CONTAINER_FACTORY)
    public void onTaskEvent(Message message) throws IOException {
        EventEnvelope<JsonNode> event = objectMapper.readValue(
                message.getBody(),
                new TypeReference<EventEnvelope<JsonNode>>() {
                });

        ProcessTaskNotificationCommand command =
                eventMapper.toCommand(event);

        ProcessResult result = eventProcessor.process(command);

        log.info(
                "Processed task event eventId={} eventType={} result={}",
                event.eventId(),
                event.eventType(),
                result);
    }
}