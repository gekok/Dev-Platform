package dev.platform.task.mock;

import dev.platform.task.application.port.out.ColumnRelationshipValidator;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicit allowlist for local development; never active without the mock profile. */
@Component
@Profile("mock")
public class MockColumnRelationshipValidator implements ColumnRelationshipValidator {
    private final Set<String> relationships;

    public MockColumnRelationshipValidator(@Value("${task.mock.columns:}") String configured) {
        relationships = Arrays.stream(configured.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (relationships.stream().anyMatch(value -> !value.matches("[1-9][0-9]*:[1-9][0-9]*:[1-9][0-9]*"))) {
            throw new IllegalArgumentException("task.mock.columns must contain project:board:column triples");
        }
    }

    @Override
    public void requireColumn(Long projectId, Long boardId, Long columnId) {
        if (!relationships.contains(projectId + ":" + boardId + ":" + columnId)) {
            throw new IllegalArgumentException("Board or column is not in the configured mock relationships");
        }
    }
}
