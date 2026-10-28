package dev.platform.user;

import dev.platform.shared.messaging.MessagingSupportConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "dev.platform.user",
        "dev.platform.shared.messaging.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
        "dev.platform.user",
        "dev.platform.shared.messaging.persistence.repository"
})
@Import(MessagingSupportConfiguration.class)
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
