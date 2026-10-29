package dev.platform.task.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class TaskAssigneeId implements Serializable {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    public TaskAssigneeId(Long taskId, UUID userId) {
        this.taskId = taskId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TaskAssigneeId that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, userId);
    }
}
