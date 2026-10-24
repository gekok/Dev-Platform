package dev.platform.task.todo.application;

import dev.platform.task.todo.api.CreateTodoTaskRequest;
import dev.platform.task.todo.api.UpdateTodoTaskRequest;
import dev.platform.task.todo.infrastructure.TodoTaskEntity;
import dev.platform.task.todo.infrastructure.TodoTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoTaskService {

    private final TodoTaskRepository tasks;

    @Transactional
    public TodoTaskEntity create(UUID ownerId, CreateTodoTaskRequest request) {
        final OffsetDateTime now = OffsetDateTime.now();
        final TodoTaskEntity task = new TodoTaskEntity();
        task.setOwnerId(ownerId);
        task.setProjectId(request.projectId());
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setStartDate(request.startDate());
        task.setDeadline(request.deadline());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return tasks.save(task);
    }

    @Transactional(readOnly = true)
    public List<TodoTaskEntity> list(UUID ownerId) {
        return tasks.findByOwnerIdOrderByDeadlineAscCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public TodoTaskEntity get(UUID ownerId, Long taskId) {
        return tasks.findByIdAndOwnerId(taskId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay task"));
    }

    @Transactional
    public TodoTaskEntity update(UUID ownerId, Long taskId, UpdateTodoTaskRequest request) {
        final TodoTaskEntity task = get(ownerId, taskId);
        if (request.title() != null) task.setTitle(request.title().trim());
        if (request.description() != null) task.setDescription(request.description());
        if (request.status() != null) task.setStatus(request.status());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.startDate() != null) task.setStartDate(request.startDate());
        if (request.deadline() != null) task.setDeadline(request.deadline());
        task.setUpdatedAt(OffsetDateTime.now());
        return tasks.save(task);
    }

    @Transactional
    public void delete(UUID ownerId, Long taskId) {
        tasks.delete(get(ownerId, taskId));
    }
}
