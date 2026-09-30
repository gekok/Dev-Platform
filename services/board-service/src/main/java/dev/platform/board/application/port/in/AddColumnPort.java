package dev.platform.board.application.port.in;

import dev.platform.board.application.command.AddColumnCommand;
import dev.platform.board.domain.aggregate.BoardAggregate;

// Cổng vào: thêm cột vào bảng.
public interface AddColumnPort {

    BoardAggregate addColumn(AddColumnCommand cmd);
}
