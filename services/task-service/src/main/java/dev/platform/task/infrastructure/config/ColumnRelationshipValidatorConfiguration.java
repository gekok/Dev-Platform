package dev.platform.task.infrastructure.config;

import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.port.out.ColumnRelationshipValidator;
import dev.platform.task.application.service.BoardStateUnavailableException;
import dev.platform.task.application.service.BoardWriteGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the Board-backed application guard into Card create/move validation. */
@Configuration(proxyBeanMethods = false)
public class ColumnRelationshipValidatorConfiguration {

    @Bean
    @ConditionalOnMissingBean(ColumnRelationshipValidator.class)
    ColumnRelationshipValidator boardColumnRelationshipValidator(BoardWriteGuard boardWriteGuard) {
        return (projectId, boardId, columnId) -> {
            try {
                boardWriteGuard.requireWritable(projectId, boardId, columnId);
            } catch (BoardStateUnavailableException exception) {
                throw new CardOperationUnavailableException();
            }
        };
    }
}
