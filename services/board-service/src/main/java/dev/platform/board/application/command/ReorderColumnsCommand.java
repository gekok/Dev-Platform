package dev.platform.board.application.command;

import java.util.List;

// Lệnh xếp lại thứ tự cột.
public record ReorderColumnsCommand(
        Long boardId,
        List<Long> orderedColumnIds
) {
}
