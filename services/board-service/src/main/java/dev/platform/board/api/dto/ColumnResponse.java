package dev.platform.board.api.dto;

// Một cột trong phản hồi bảng.
public record ColumnResponse(
        Long id,
        String name,
        int position
) {
}
