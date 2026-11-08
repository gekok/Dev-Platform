package dev.platform.board.application.port.out;

public interface InternalServiceAuthenticationPort {
    void verify(String serviceToken);
}
