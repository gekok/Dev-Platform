package dev.platform.board.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-07T00:00:00Z");
    private final Project active = new Project(1L, "Kanban", Project.Status.ACTIVE,
            UUID.randomUUID(), now, now);

    @Test
    void trimsProjectNameAndRejectsBlankOrOversizedValues() {
        assertEquals("Project", Project.validateName("  Project  "));
        assertThrows(ProjectRuleViolation.class, () -> Project.validateName("   "));
        assertThrows(ProjectRuleViolation.class, () -> Project.validateName("x".repeat(256)));
    }

    @Test
    void permitsReopeningButRejectsDirectTerminalStatusTransition() {
        Project completed = active.changeStatus(Project.Status.COMPLETED, now.plusDays(1));
        assertEquals(Project.Status.ACTIVE,
                completed.changeStatus(Project.Status.ACTIVE, now.plusDays(2)).status());
        ProjectRuleViolation violation = assertThrows(ProjectRuleViolation.class,
                () -> completed.changeStatus(Project.Status.CANCELLED, now.plusDays(2)));
        assertEquals("INVALID_STATUS_TRANSITION", violation.code());
    }

    @Test
    void settingTheCurrentStatusIsANoOp() {
        assertSame(active, active.changeStatus(Project.Status.ACTIVE, now.plusDays(1)));
    }
}
