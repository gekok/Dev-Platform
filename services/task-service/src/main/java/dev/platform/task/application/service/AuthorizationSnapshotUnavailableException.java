package dev.platform.task.application.service;

public class AuthorizationSnapshotUnavailableException extends RuntimeException {

    public AuthorizationSnapshotUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthorizationSnapshotUnavailableException(String message) {
        super(message);
    }
}
