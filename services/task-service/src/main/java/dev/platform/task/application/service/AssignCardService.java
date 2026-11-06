package dev.platform.task.application.service;

import dev.platform.task.application.command.AssignCardCommand;
import dev.platform.task.application.exception.CardNotFoundException;
import dev.platform.task.application.port.in.AssignCardPort;
import dev.platform.task.application.port.out.AssignmentLockPort;
import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(
        name = "dev-platform.security.trusted-gateway-identity.enabled",
        havingValue = "true")
@RequiredArgsConstructor
public class AssignCardService implements AssignCardPort {
    private final SaveCardPort cards;
    private final AssignmentLockPort assignmentLock;

    @Override
    @Transactional
    public CardAggregate assign(AssignCardCommand cmd) {
        if (cmd.projectId() == null || cmd.cardId() == null || cmd.actorId() == null) {
            throw new IllegalArgumentException("Project, card and actor are required");
        }
        if (cmd.assigneeId() != null) {
            if (cmd.assigneeMembershipVersion() == null || cmd.assigneeMembershipVersion() < 1) {
                throw new IllegalArgumentException("Active assignee membership version is required");
            }
            assignmentLock.lock(cmd.projectId(), cmd.assigneeId());
        }
        CardAggregate card = cards.findForUpdate(cmd.projectId(), cmd.cardId())
                .orElseThrow(CardNotFoundException::new);
        if (!card.assign(cmd.assigneeId(), cmd.assigneeMembershipVersion(), cmd.actorId())) {
            return card;
        }
        return cards.save(card);
    }
}
