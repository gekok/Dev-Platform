package dev.platform.shared.messaging;

import dev.platform.shared.messaging.persistence.entity.InboxEventEntity;
import dev.platform.shared.messaging.persistence.repository.InboxEventJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "shared.sqlserver.tests", matches = "true")
@SpringBootTest(classes = InboxSqlServerTest.TestApplication.class, properties = {
        "spring.config.import=optional:file:../../.env[.properties]",
        "spring.datasource.url=${INBOX_TEST_DB_URL:jdbc:sqlserver://localhost;instanceName=PHUC;databaseName=kanban_user_db;encrypt=true;trustServerCertificate=true}",
        "spring.datasource.username=${DB_USERNAME}",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.main.web-application-type=none"
})
class InboxSqlServerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = InboxEventEntity.class)
    @EnableJpaRepositories(basePackageClasses = InboxEventJpaRepository.class)
    @Import(InboxEventRecorder.class)
    static class TestApplication {
    }

    private final UUID eventId = UUID.randomUUID();
    private final String consumer = "test.inbox." + UUID.randomUUID();
    private final String otherConsumer = "test.inbox." + UUID.randomUUID();

    @Autowired InboxEventRecorder recorder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanOnlyThisTestsRecords() {
        jdbc.update("DELETE FROM inbox_events WHERE event_id = ? AND consumer_name IN (?, ?)",
                eventId, consumer, otherConsumer);
    }

    @Test
    void duplicateIsIgnoredButAnotherConsumerCanProcessSameEvent() {
        var tx = new TransactionTemplate(transactionManager);
        assertTrue(Boolean.TRUE.equals(tx.execute(status -> recorder.recordIfNew(consumer, eventId, "test.event"))));
        assertFalse(Boolean.TRUE.equals(tx.execute(status -> recorder.recordIfNew(consumer, eventId, "test.event"))));
        assertTrue(Boolean.TRUE.equals(tx.execute(status -> recorder.recordIfNew(otherConsumer, eventId, "test.event"))));
        assertEquals(2, count());
    }

    @Test
    void rolledBackInboxRecordCanBeProcessedAgain() {
        var tx = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            assertTrue(recorder.recordIfNew(consumer, eventId, "test.event"));
            throw new IllegalStateException("simulated business failure");
        }));
        assertEquals(0, count());
        assertTrue(Boolean.TRUE.equals(tx.execute(status -> recorder.recordIfNew(consumer, eventId, "test.event"))));
        assertEquals(1, count());
    }

    @Test
    void simultaneousDuplicateProducesOneCommitAndCanBeRetried() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processConcurrently(barrier));
            var second = executor.submit(() -> processConcurrently(barrier));
            boolean firstCommitted = first.get(20, TimeUnit.SECONDS);
            boolean secondCommitted = second.get(20, TimeUnit.SECONDS);
            assertTrue(firstCommitted ^ secondCommitted, "exactly one transaction should commit");
            assertEquals(1, count());
            var tx = new TransactionTemplate(transactionManager);
            assertFalse(Boolean.TRUE.equals(tx.execute(status -> recorder.recordIfNew(consumer, eventId, "test.event"))));
        }
    }

    private boolean processConcurrently(CyclicBarrier barrier) {
        try {
            return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
                boolean inserted = recorder.recordIfNew(consumer, eventId, "test.event");
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not synchronize concurrent transactions", exception);
                }
                return inserted;
            }));
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sqlException
                        && (sqlException.getErrorCode() == 2627 || sqlException.getErrorCode() == 2601)
                        && sqlException.getMessage().contains("inbox_events")) {
                    return false;
                }
            }
            throw exception;
        }
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inbox_events WHERE event_id = ? AND consumer_name IN (?, ?)",
                Integer.class, eventId, consumer, otherConsumer);
    }
}
