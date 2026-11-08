package dev.platform.task.infrastructure.adapter;

import java.util.Optional;

import dev.platform.task.application.port.out.BoardProjectLookupPort;
import dev.platform.task.application.port.out.BoardStateReaderPort;
import dev.platform.task.application.service.InvalidBoardTargetException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapts the secured Board state lookup for Task's WebSocket authorization flow. */
@Component
@RequiredArgsConstructor
public class BoardProjectLookupAdapter implements BoardProjectLookupPort {

    private final BoardStateReaderPort boardStateReader;

    @Override
    public Optional<Long> findProjectId(Long boardId) {
        try {
            return Optional.of(boardStateReader.getBoardState(boardId).projectId());
        } catch (InvalidBoardTargetException exception) {
            return Optional.empty();
        }
    }
}
