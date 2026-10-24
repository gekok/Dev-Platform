package dev.platform.user.application.port.out;

import java.util.UUID;

public interface TokenReaderPort {
    UUID currentUserId(String authorizationHeader);
}
