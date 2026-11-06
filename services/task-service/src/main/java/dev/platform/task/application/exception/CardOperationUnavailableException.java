package dev.platform.task.application.exception;

public class CardOperationUnavailableException extends RuntimeException {
    public CardOperationUnavailableException() {
        super("Card operation is not available");
    }
}
