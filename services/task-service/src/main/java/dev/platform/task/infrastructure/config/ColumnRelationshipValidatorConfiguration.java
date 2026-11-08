package dev.platform.task.infrastructure.config;

import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.port.out.ColumnRelationshipValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fails closed until a trusted Board/Column relationship adapter is provided. */
@Configuration(proxyBeanMethods = false)
public class ColumnRelationshipValidatorConfiguration {

    @Bean
    @ConditionalOnMissingBean(ColumnRelationshipValidator.class)
    ColumnRelationshipValidator unavailableColumnRelationshipValidator() {
        return (projectId, boardId, columnId) -> {
            throw new CardOperationUnavailableException();
        };
    }
}
