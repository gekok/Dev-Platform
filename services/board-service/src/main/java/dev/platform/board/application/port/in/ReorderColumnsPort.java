package dev.platform.board.application.port.in;

import dev.platform.board.application.command.ReorderColumnsCommand;
import dev.platform.board.domain.aggregate.BoardAggregate;

// Cổng vào: xếp lại thứ tự cột.
public interface ReorderColumnsPort {

    BoardAggregate reorder(ReorderColumnsCommand cmd);
}
