package dev.platform.board.application.port.in;

import dev.platform.board.application.command.*;
import dev.platform.board.application.query.*;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.domain.aggregate.ProjectMembership;

public interface BoardUseCasePort {
    Board create(CreateProjectBoardCommand command);

    BoardPage<BoardSummary> list(BoardsQuery query);

    BoardView get(BoardQuery query);

    Board rename(BoardNameCommand command);

    Board changeStatus(BoardStatusCommand command);

    Board addColumn(AddBoardColumnCommand command);

    Board renameColumn(RenameBoardColumnCommand command);

    Board reorderColumns(ReorderBoardColumnsCommand command);

    void deleteColumn(DeleteBoardColumnCommand command);

    BoardAccess verifyBoard(InternalBoardQuery query);
}
