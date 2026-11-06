package dev.platform.user.application.port.out;

import java.util.UUID;

public interface TokenPort {
    String issue(UUID userId, String email);
}
