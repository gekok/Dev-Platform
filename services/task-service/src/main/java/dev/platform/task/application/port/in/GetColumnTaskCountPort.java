package dev.platform.task.application.port.in;

public interface GetColumnTaskCountPort {
    long countByColumnId(Long columnId);
}
