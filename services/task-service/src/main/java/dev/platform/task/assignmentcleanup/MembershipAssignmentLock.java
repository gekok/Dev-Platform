package dev.platform.task.assignmentcleanup;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import dev.platform.task.application.port.out.AssignmentLockPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MembershipAssignmentLock implements AssignmentLockPort {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void lock(Long projectId, java.util.UUID userId) {
        String lockKey = "membership-assignment:" + projectId + ":" + userId;
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    // SQL Server can defer starting an implicit JDBC transaction until a table is read.
                    "DECLARE @transaction_marker int; SELECT @transaction_marker = COUNT(*) FROM sys.tables; "
                            + "DECLARE @result int; EXEC @result = sys.sp_getapplock "
                            + "@Resource = ?, @LockMode = 'Exclusive', @LockOwner = 'Transaction', "
                            + "@LockTimeout = 10000; SELECT @result")) {
                statement.setString(1, lockKey);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException("No result for membership assignment lock " + lockKey);
                    }
                    int status = result.getInt(1);
                    if (status < 0) {
                        throw new IllegalStateException(
                                "Could not acquire membership assignment lock " + lockKey + ": status " + status);
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not acquire membership assignment lock " + lockKey, exception);
            }
            return null;
        });
    }
}
