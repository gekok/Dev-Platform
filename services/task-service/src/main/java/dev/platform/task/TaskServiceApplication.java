package dev.platform.task;

import dev.platform.shared.messaging.MessagingSupportConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "dev.platform.task",
        "dev.platform.shared.messaging.persistence.entity"
})
@EnableJpaRepositories(basePackages = {
        "dev.platform.task",
        "dev.platform.shared.messaging.persistence.repository"
})
@Import(MessagingSupportConfiguration.class)
public class TaskServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
