package dev.platform.task.mock;

import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.port.out.ColumnRelationshipValidator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!mock")
public class ColumnValidationConfiguration {
    @Bean
    @ConditionalOnMissingBean(ColumnRelationshipValidator.class)
    ColumnRelationshipValidator unavailableColumnValidator() {
        return (projectId, boardId, columnId) -> { throw new CardOperationUnavailableException(); };
    }
}
