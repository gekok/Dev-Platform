package dev.platform.task.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import dev.platform.task.application.command.CreateCardCommand;
import dev.platform.task.application.port.in.CreateCardPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "task.mock.columns=987654:987654:987654")
@ActiveProfiles("mock")
@EnabledIfSystemProperty(named = "task.sqlserver.integration", matches = "true")
class CardCreateSqlServerTest {
    @Autowired CreateCardPort createCard;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void createThenRollbackDoesNotPersistCard() {
        String title = "rollback-" + UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            var created = createCard.create(new CreateCardCommand(987654L, 987654L, 987654L,
                    title, null, null, null, null, UUID.randomUUID()));
            assertNotNull(created.getId());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE title = ?", Integer.class, title));
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE title = ?", Integer.class, title));
    }
}
