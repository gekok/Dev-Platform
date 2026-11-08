package dev.platform.task.application.service;

public class BoardStateUnavailableException extends RuntimeException {
    public BoardStateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
