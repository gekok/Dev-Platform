package dev.platform.task.api;

import dev.platform.task.application.port.in.GetColumnTaskCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/columns")
@RequiredArgsConstructor
public class InternalColumnController {
    private final GetColumnTaskCountPort taskCounts;

    @Value("${dev-platform.security.internal-service-token:}")
    private String configuredToken;

    @GetMapping("/{columnId}/task-count")
    public TaskCountResponse countTasks(
            @PathVariable Long columnId,
            @RequestHeader(name = "X-Service-Token", required = false) String serviceToken) {
        if (configuredToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Internal service authentication is not configured.");
        }
        if (serviceToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Service authentication failed.");
        }
        return new TaskCountResponse(taskCounts.countByColumnId(columnId));
    }

    public record TaskCountResponse(long count) {
    }
}
