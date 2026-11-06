package dev.platform.task.application.service;

import dev.platform.task.application.command.CreateCardCommand;
import dev.platform.task.application.port.in.CreateCardPort;
import dev.platform.task.application.port.out.CardEventPublisherPort;
import dev.platform.task.application.port.out.ColumnRelationshipValidator;
import dev.platform.task.application.port.out.MoveCardStore;
import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCardService implements CreateCardPort {
    private final ColumnRelationshipValidator columns;
    private final MoveCardStore positions;
    private final SaveCardPort cards;
    private final CardEventPublisherPort events;

    @Override
    @Transactional
    public CardAggregate create(CreateCardCommand cmd) {
        if (cmd.projectId() == null || cmd.boardId() == null || cmd.columnId() == null) {
            throw new IllegalArgumentException("Project, board and column are required");
        }
        positions.lockBoard(cmd.projectId(), cmd.boardId());
        columns.requireColumn(cmd.projectId(), cmd.boardId(), cmd.columnId());
        int position = positions.nextPosition(cmd.projectId(), cmd.boardId(), cmd.columnId());
        CardAggregate created = cards.save(CardAggregate.create(cmd.projectId(), cmd.boardId(), cmd.columnId(), cmd.title(),
                cmd.description(), cmd.assigneeId(), cmd.assigneeMembershipVersion(), cmd.creatorId(),
                cmd.dueDate(), position));
        events.cardCreated(created.getId(), created.getBoardId(), created.getColumnId());
        return created;
    }
}
