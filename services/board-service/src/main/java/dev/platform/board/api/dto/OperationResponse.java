package dev.platform.board.api.dto;

import java.util.UUID;

public record OperationResponse(UUID operationId, String status, String statusUrl) {
    public static OperationResponse pending(UUID id) {
        return new OperationResponse(id, "PENDING", "/api/v1/operations/" + id);
    }
}
