package dev.platform.board.api.dto;

import dev.platform.board.application.query.BoardAccess;
import java.util.List;

public record InternalBoardResponse(
        Long boardId, Long projectId, String boardStatus, String projectStatus, List<InternalColumn> columns) {

    public static InternalBoardResponse from(BoardAccess access) {
        return new InternalBoardResponse(access.boardId(), access.projectId(),
                access.boardStatus(), access.projectStatus(),
                access.columns().stream().map(column ->
                        new InternalColumn(column.id(), column.name(), column.position())).toList());
    }

    public record InternalColumn(Long id, String name, int position) {
    }
}
