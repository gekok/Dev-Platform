package dev.platform.task.application.service;

import dev.platform.task.application.port.out.BoardStateReaderPort;
import dev.platform.task.application.port.out.CardBoardLookupPort;
import dev.platform.task.application.query.BoardStateSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BoardWriteGuard {
    private final BoardStateReaderPort boardStates;
    private final CardBoardLookupPort cards;

    public void requireWritable(Long projectId, Long boardId, Long columnId) {
        BoardStateSnapshot board = boardStates.getBoardState(boardId);
        if (board == null || !projectId.equals(board.projectId())
                || board.columns() == null
                || board.columns().stream().noneMatch(column -> Objects.equals(column.id(), columnId))) {
            throw new InvalidBoardTargetException();
        }
        if (!"ACTIVE".equals(board.projectStatus())) {
            throw new BoardNotWritableException("PROJECT_NOT_ACTIVE");
        }
        if (!"ACTIVE".equals(board.boardStatus())) {
            throw new BoardNotWritableException("BOARD_NOT_ACTIVE");
        }
    }

    public void requireWritableForCard(Long projectId, Long cardId, Long targetColumnId) {
        cards.findBoardId(projectId, cardId)
                .ifPresent(boardId -> requireWritable(projectId, boardId, targetColumnId));
    }
}
