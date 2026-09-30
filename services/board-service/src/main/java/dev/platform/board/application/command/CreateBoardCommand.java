package dev.platform.board.application.command;

import java.util.List;
import java.util.UUID;

// Lệnh tạo bảng mới.
public record CreateBoardCommand(
        String name,
        String key,
        UUID ownerId,
        List<String> columns
) {
}
