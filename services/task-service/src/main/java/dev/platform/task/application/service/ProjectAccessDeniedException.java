package dev.platform.task.application.service;

public class ProjectAccessDeniedException extends RuntimeException {

    public ProjectAccessDeniedException(String message) {
        super(message);
    }
}
