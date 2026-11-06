package dev.platform.task.infrastructure.adapter;

import java.util.ArrayList;
import java.util.List;
import dev.platform.task.application.port.out.MoveCardStore;
import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.application.exception.CardNotFoundException;
import dev.platform.task.domain.aggregate.CardAggregate;
import dev.platform.task.infrastructure.persistence.entity.CardEntity;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MoveCardJpaAdapter implements MoveCardStore {
    private final CardJpaRepository cards;
    private final SaveCardPort cardReader;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    @Override
    public CardAggregate find(Long projectId, Long cardId) {
        return cardReader.findById(projectId, cardId).orElseThrow(CardNotFoundException::new);
    }

    @Override
    public void lockBoard(Long projectId, Long boardId) {
        Integer result = jdbc.queryForObject("DECLARE @result int; "
                + "EXEC @result = sys.sp_getapplock @Resource = ?, @LockMode = 'Exclusive', "
                + "@LockOwner = 'Transaction', @LockTimeout = 10000; SELECT @result", Integer.class,
                "task-card-board:" + projectId + ":" + boardId);
        if (result == null || result < 0) throw new IllegalStateException("Could not acquire board lock");
    }

    @Override
    public int nextPosition(Long projectId, Long boardId, Long columnId) {
        Integer max = cards.maxPosition(projectId, boardId, columnId);
        if (max == null) return 0;
        if (max == Integer.MAX_VALUE) throw new IllegalStateException("Column position limit reached");
        return Math.max(0, max + 1);
    }

    @Override
    public CardAggregate reload(Long projectId, Long cardId) {
        CardEntity entity = cards.findByIdAndProjectId(cardId, projectId).orElseThrow(CardNotFoundException::new);
        entityManager.refresh(entity);
        if (!projectId.equals(entity.getProjectId())) throw new CardNotFoundException();
        return find(projectId, cardId);
    }

    @Override
    public CardAggregate reorder(Long projectId, Long boardId, Long cardId, Long sourceColumnId,
            Long destinationColumnId, int targetIndex) {
        List<CardEntity> source = new ArrayList<>(cards
                .findByProjectIdAndBoardIdAndColumnIdOrderByPositionDescIdDesc(projectId, boardId, sourceColumnId));
        CardEntity moving = source.stream().filter(card -> cardId.equals(card.getId())).findFirst()
                .orElseThrow(CardNotFoundException::new);
        source.remove(moving);
        boolean sameColumn = sourceColumnId.equals(destinationColumnId);
        List<CardEntity> destination = sameColumn ? source : new ArrayList<>(cards
                .findByProjectIdAndBoardIdAndColumnIdOrderByPositionDescIdDesc(projectId, boardId, destinationColumnId));
        if (targetIndex > destination.size()) throw new IllegalArgumentException("Position outside destination column");
        moving.setColumnId(destinationColumnId);
        destination.add(targetIndex, moving);
        if (!sameColumn) setPositions(source);
        setPositions(destination);
        cards.flush();
        return find(projectId, cardId);
    }

    private static void setPositions(List<CardEntity> cards) {
        for (int i = 0; i < cards.size(); i++) cards.get(i).setPosition(cards.size() - i - 1);
    }
}
