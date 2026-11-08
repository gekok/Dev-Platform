package dev.platform.task.application.service;

public class BoardNotWritableException extends RuntimeException {
    private final String code;

    public BoardNotWritableException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
