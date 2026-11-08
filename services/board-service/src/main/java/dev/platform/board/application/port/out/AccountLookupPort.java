package dev.platform.board.application.port.out;

import java.util.UUID;

public interface AccountLookupPort {
    boolean isActive(UUID userId);
}
