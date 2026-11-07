package dev.platform.user.api.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
    String code,
    String message,
    OffsetDateTime timestamp,
    String path
) {
}
