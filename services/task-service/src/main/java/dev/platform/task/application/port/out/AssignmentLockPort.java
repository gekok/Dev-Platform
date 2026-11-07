package dev.platform.task.application.port.out;

import java.util.UUID;

public interface AssignmentLockPort {
    void lock(Long projectId, UUID userId);
}
