package dev.platform.board.application.command;

import java.util.List;
import java.util.UUID;

public record ReorderBoardColumnsCommand(Long boardId, List<Long> columnIds, UUID actorId) {
}
