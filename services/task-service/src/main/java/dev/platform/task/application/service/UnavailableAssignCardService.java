package dev.platform.task.application.service;

import dev.platform.task.application.command.AssignCardCommand;
import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.port.in.AssignCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Keeps assignment unavailable until task-service receives trusted identity from the Gateway. */
@Service
@ConditionalOnProperty(
        name = "dev-platform.security.trusted-gateway-identity.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class UnavailableAssignCardService implements AssignCardPort {

    @Override
    public CardAggregate assign(AssignCardCommand cmd) {
        throw new CardOperationUnavailableException();
    }
}
