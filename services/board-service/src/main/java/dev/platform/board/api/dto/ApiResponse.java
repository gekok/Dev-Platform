package dev.platform.board.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApiResponse<T>(T data, Meta meta) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(UUID.randomUUID().toString(), OffsetDateTime.now()));
    }

    public record Meta(String correlationId, OffsetDateTime timestamp) {
    }
}
