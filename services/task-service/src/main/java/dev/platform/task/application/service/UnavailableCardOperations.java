package dev.platform.task.application.service;

import dev.platform.task.application.exception.CardOperationUnavailableException;

import dev.platform.task.application.port.in.AssignCardPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fail closed until the corresponding use cases are implemented. */
@Configuration(proxyBeanMethods = false)
public class UnavailableCardOperations {
    @Bean
    @ConditionalOnMissingBean(AssignCardPort.class)
    AssignCardPort unavailableAssignCard() {
        return command -> { throw new CardOperationUnavailableException(); };
    }

}
