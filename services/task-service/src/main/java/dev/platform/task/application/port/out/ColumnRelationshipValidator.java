package dev.platform.task.application.port.out;

public interface ColumnRelationshipValidator {
    void requireColumn(Long projectId, Long boardId, Long columnId);
}
