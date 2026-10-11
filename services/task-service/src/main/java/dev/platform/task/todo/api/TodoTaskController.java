package dev.platform.task.todo.api;

import dev.platform.task.todo.application.TodoTaskService;
import dev.platform.task.todo.infrastructure.JwtCurrentUserIdResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/todo/tasks")
@RequiredArgsConstructor
public class TodoTaskController {

    private final TodoTaskService service;
    private final JwtCurrentUserIdResolver currentUser;

    @PostMapping
    public ResponseEntity<TodoTaskResponse> create(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody CreateTodoTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TodoTaskResponse.from(service.create(currentUser.resolve(authorization), request)));
    }

    @GetMapping
    public List<TodoTaskResponse> list(@RequestHeader("Authorization") String authorization) {
        return service.list(currentUser.resolve(authorization)).stream()
                .map(TodoTaskResponse::from)
                .toList();
    }

    @GetMapping("/{taskId}")
    public TodoTaskResponse get(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long taskId) {
        return TodoTaskResponse.from(service.get(currentUser.resolve(authorization), taskId));
    }

    @PatchMapping("/{taskId}")
    public TodoTaskResponse update(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTodoTaskRequest request) {
        return TodoTaskResponse.from(
                service.update(currentUser.resolve(authorization), taskId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long taskId) {
        service.delete(currentUser.resolve(authorization), taskId);
        return ResponseEntity.noContent().build();
    }
}
