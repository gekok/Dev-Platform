package dev.platform.task.application.service;

public class InvalidBoardTargetException extends RuntimeException {
    public InvalidBoardTargetException() {
        super("Board, Project, or Column target is invalid.");
    }
}
