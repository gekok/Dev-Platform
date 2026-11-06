package dev.platform.task.infrastructure.adapter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import dev.platform.task.application.port.out.CommentStore;
import dev.platform.task.application.exception.CardNotFoundException;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;
import dev.platform.task.infrastructure.persistence.entity.CardCommentEntity;
import dev.platform.task.infrastructure.persistence.repository.CardCommentJpaRepository;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentJpaAdapter implements CommentStore {
    private final CardJpaRepository cards;
    private final CardCommentJpaRepository comments;

    @Override
    public boolean cardExists(Long projectId, Long cardId) {
        return cards.findByIdAndProjectId(cardId, projectId).isPresent();
    }

    @Override
    public CommentValue save(Long projectId, Long cardId, UUID authorId, String content) {
        var card = cards.findByIdAndProjectId(cardId, projectId).orElseThrow(CardNotFoundException::new);
        return value(comments.save(CardCommentEntity.builder().card(card).authorId(authorId)
                .content(content).createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build()));
    }

    @Override
    public List<CommentValue> findAll(Long projectId, Long cardId) {
        return comments.findByCard_ProjectIdAndCard_IdOrderByIdAsc(projectId, cardId).stream()
                .map(CommentJpaAdapter::value).toList();
    }

    private static CommentValue value(CardCommentEntity entity) {
        return new CommentValue(entity.getId(), entity.getAuthorId(), entity.getContent(), entity.getCreatedAt());
    }
}
