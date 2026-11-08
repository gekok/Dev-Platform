package dev.platform.task.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.command.CreateCardCommand;
import dev.platform.task.application.port.in.CreateCardPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@EnabledIfSystemProperty(named = "task.sqlserver.integration", matches = "true")
class CardCreateSqlServerTest {
    @Autowired CreateCardPort createCard;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createFailsClosedWithoutTrustedColumnAdapter() {
        String title = "unverified-column-" + UUID.randomUUID();
        assertThrows(CardOperationUnavailableException.class,
                () -> createCard.create(new CreateCardCommand(987654L, 987654L, 987654L,
                        title, null, null, null, null, UUID.randomUUID())));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE title = ?", Integer.class, title));
    }
}
