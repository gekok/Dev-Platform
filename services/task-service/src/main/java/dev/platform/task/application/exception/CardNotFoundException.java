package dev.platform.task.application.exception;

public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException() { super("Card not found"); }
}
