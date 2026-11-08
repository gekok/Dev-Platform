package dev.platform.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.shared.messaging.MessagingSupportConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {
    "dev.platform.user.infrastructure.persistence.entity",
    "dev.platform.shared.messaging.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
    "dev.platform.user.infrastructure.persistence.repository",
    "dev.platform.shared.messaging.persistence.repository"
})
@Import(MessagingSupportConfiguration.class)
@EnableScheduling
public class UserServiceApplication {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper userServiceObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
