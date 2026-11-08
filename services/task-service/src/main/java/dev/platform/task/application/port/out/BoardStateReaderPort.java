package dev.platform.task.application.port.out;

import dev.platform.task.application.query.BoardStateSnapshot;

public interface BoardStateReaderPort {
    BoardStateSnapshot getBoardState(Long boardId);
}
