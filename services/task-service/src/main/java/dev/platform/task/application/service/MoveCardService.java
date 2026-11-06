package dev.platform.task.application.service;

import dev.platform.task.application.command.MoveCardCommand;
import dev.platform.task.application.port.in.MoveCardPort;
import dev.platform.task.application.port.out.CardEventPublisherPort;
import dev.platform.task.application.port.out.ColumnRelationshipValidator;
import dev.platform.task.application.port.out.MoveCardStore;
import dev.platform.task.domain.aggregate.CardAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoveCardService implements MoveCardPort {
    private final MoveCardStore store;
    private final ColumnRelationshipValidator columns;
    private final CardEventPublisherPort events;

    @Override
    @Transactional
    public CardAggregate move(MoveCardCommand command) {
        if (command.toColumnId() == null || command.toPosition() < 0) {
            throw new IllegalArgumentException("Destination column and non-negative position are required");
        }
        CardAggregate initial = store.find(command.projectId(), command.cardId());
        store.lockBoard(command.projectId(), initial.getBoardId());
        CardAggregate current = store.reload(command.projectId(), command.cardId());
        if (!initial.getBoardId().equals(current.getBoardId())) {
            throw new IllegalStateException("Card board changed while acquiring lock");
        }
        columns.requireColumn(command.projectId(), current.getBoardId(), current.getColumnId());
        columns.requireColumn(command.projectId(), current.getBoardId(), command.toColumnId());
        CardAggregate moved = store.reorder(command.projectId(), current.getBoardId(), command.cardId(),
                current.getColumnId(), command.toColumnId(), command.toPosition());
        events.cardMoved(moved.getId(), moved.getBoardId(), moved.getColumnId(), moved.getPosition());
        return moved;
    }
}
