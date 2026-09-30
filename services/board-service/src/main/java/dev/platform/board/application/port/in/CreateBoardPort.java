package dev.platform.board.application.port.in;

import dev.platform.board.application.command.CreateBoardCommand;
import dev.platform.board.domain.aggregate.BoardAggregate;

// Cổng vào: tạo bảng mới.
public interface CreateBoardPort {

    BoardAggregate create(CreateBoardCommand cmd);
}
