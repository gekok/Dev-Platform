package dev.platform.task.infrastructure.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;
import dev.platform.task.domain.aggregate.CardAggregate.Status;
import dev.platform.task.infrastructure.persistence.entity.CardEntity;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import lombok.RequiredArgsConstructor;

// Adapter JPA cho cổng ra SaveCardPort.
@Component
@RequiredArgsConstructor
public class SaveCardAdapter implements SaveCardPort {

    private final CardJpaRepository cardJpaRepository;

    @Override
    public CardAggregate save(CardAggregate card) {
        return toAggregate(cardJpaRepository.save(toEntity(card)));
    }

    @Override
    public Optional<CardAggregate> findById(Long id) {
        return cardJpaRepository.findById(id).map(this::toAggregate);
    }

    @Override
    public List<CardAggregate> findByBoardIdOrderByPosition(Long boardId) {
        return cardJpaRepository.findByBoardIdOrderByPositionAsc(boardId).stream()
                .map(this::toAggregate)
                .toList();
    }

    @Override
    public List<CardAggregate> findByBoardIdAndColumnId(Long boardId, Long columnId) {
        return cardJpaRepository.findByBoardIdAndColumnIdOrderByPositionAsc(boardId, columnId).stream()
                .map(this::toAggregate)
                .toList();
    }

    // Chuyển Aggregate sang Entity để lưu.
    private CardEntity toEntity(CardAggregate card) {
        return CardEntity.builder()
                .id(card.getId())
                .boardId(card.getBoardId())
                .columnId(card.getColumnId())
                .title(card.getTitle())
                .description(card.getDescription())
                .status(card.getStatus() == null ? "TODO" : card.getStatus().name())
                .position(card.getPosition())
                .assigneeId(card.getAssigneeId())
                .dueDate(card.getDueDate())
                .build();
    }

    // Chuyển Entity sang Aggregate để trả về (chưa nạp comments kỳ này).
    private CardAggregate toAggregate(CardEntity entity) {
        CardAggregate card = new CardAggregate();
        card.setId(entity.getId());
        card.setBoardId(entity.getBoardId());
        card.setColumnId(entity.getColumnId());
        card.setTitle(entity.getTitle());
        card.setDescription(entity.getDescription());
        card.setStatus(parseStatus(entity.getStatus()));
        card.setPosition(entity.getPosition());
        card.setAssigneeId(entity.getAssigneeId());
        card.setDueDate(entity.getDueDate());
        card.setComments(new java.util.ArrayList<CommentValue>());
        return card;
    }

    // Parse status string, lạ thì về TODO.
    private Status parseStatus(String raw) {
        if (raw == null) {
            return Status.TODO;
        }
        try {
            return Status.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return Status.TODO;
        }
    }
}
