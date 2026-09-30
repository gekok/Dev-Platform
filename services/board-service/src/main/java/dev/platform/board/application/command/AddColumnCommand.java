package dev.platform.board.application.command;

import java.util.List;
import java.util.UUID;

// Lệnh thêm cột vào bảng.
public record AddColumnCommand(
        Long boardId,
        String name
) {
}
