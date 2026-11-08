package dev.platform.task.infrastructure.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.platform.task.application.command.CreateCardCommand;
import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.port.in.CreateCardPort;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "task.sqlserver.integration", matches = "true")
class BoardLockSqlServerTest {
    @Autowired MoveCardJpaAdapter adapter;
    @Autowired TransactionTemplate transactions;
    @Autowired CreateCardPort createCard;
    @Autowired JdbcTemplate jdbc;

    @Test
    void lockWorksBeforeAnyBusinessQueryInTransaction() {
        transactions.executeWithoutResult(status -> adapter.lockBoard(987654L, 987654L));
    }

    @Test
    void unavailableColumnValidatorDoesNotWriteCard() {
        Long before = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM cards WHERE project_id = 987654", Long.class);
        assertThrows(CardOperationUnavailableException.class, () -> createCard.create(new CreateCardCommand(
                987654L, 987654L, 987654L, "Unavailable column", null, null, null, null, UUID.randomUUID())));
        Long after = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM cards WHERE project_id = 987654", Long.class);
        assertEquals(before, after);
    }

    @Test
    void concurrentTransactionsSerializeOnSameBoard() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var secondLocked = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> transactions.executeWithoutResult(status -> {
                adapter.lockBoard(987654L, 987655L);
                locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lock release timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var second = workers.submit(() -> transactions.executeWithoutResult(status -> {
                    secondStarted.countDown();
                    adapter.lockBoard(987654L, 987655L);
                    secondLocked.countDown();
                }));
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertFalse(secondLocked.await(300, TimeUnit.MILLISECONDS));
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                assertEquals(0, secondLocked.getCount());
            } finally {
                release.countDown();
            }
        }
    }
}
