package dev.platform.task.assignmentcleanup;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MembershipAssignmentLock {

    private final JdbcTemplate jdbcTemplate;

    public void lock(Long projectId, java.util.UUID userId) {
        String lockKey = projectId + ":" + userId;
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, lockKey);
                statement.execute();
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not acquire membership assignment lock", exception);
            }
            return null;
        });
    }
}
