package dev.platform.task.application.service;

import dev.platform.task.application.exception.CardOperationUnavailableException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import dev.platform.task.application.command.AddCommentCommand;
import dev.platform.task.application.port.in.AddCommentPort;
import dev.platform.task.application.port.out.CardEventPublisherPort;
import dev.platform.task.application.port.out.CommentStore;
import dev.platform.task.application.port.out.TaskNotificationEventPort;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService implements AddCommentPort {
    private final CommentStore comments;
    private final ProjectAuthorizationGuard authorization;
    private final CardEventPublisherPort events;
    private final TaskNotificationEventPort notificationEvents;

    @Value("${dev-platform.security.trusted-gateway-identity.enabled:false}")
    private boolean trustedGatewayIdentityEnabled;

    @Override
    @Transactional
    public CommentValue addComment(AddCommentCommand cmd) {
        if (cmd == null || cmd.projectId() == null || cmd.cardId() == null || cmd.authorId() == null) {
            throw new IllegalArgumentException("Project, card and author are required");
        }
        if (!trustedGatewayIdentityEnabled) {
            throw new CardOperationUnavailableException();
        }
        if (cmd.content() == null || cmd.content().isBlank()) {
            throw new IllegalArgumentException("Comment content is required");
        }
        Long boardId = comments.requireBoardId(cmd.projectId(), cmd.cardId());
        LinkedHashSet<UUID> distinctMentionIds = new LinkedHashSet<>();
        if (cmd.mentionedUserIds() != null) {
            for (UUID userId : cmd.mentionedUserIds()) {
                if (userId == null) throw new IllegalArgumentException("Mention user is required");
                distinctMentionIds.add(userId);
            }
        }
        List<UUID> recipients = new ArrayList<>();
        for (UUID userId : distinctMentionIds) {
            authorization.requireProjectMember(cmd.projectId(), userId);
            if (!userId.equals(cmd.authorId())) {
                recipients.add(userId);
            }
        }
        CommentValue comment = comments.save(cmd.projectId(), cmd.cardId(), cmd.authorId(), cmd.content());
        notificationEvents.commentMentioned(cmd.projectId(), boardId, cmd.cardId(), comment.id(), recipients);
        events.commentAdded(cmd.cardId(), boardId, comment.id());
        return comment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentValue> getComments(Long projectId, Long cardId) {
        comments.requireBoardId(projectId, cardId);
        return comments.findAll(projectId, cardId);
    }
}
