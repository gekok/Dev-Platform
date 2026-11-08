package dev.platform.board.application.service;

import dev.platform.board.application.port.out.AccountLookupPort;
import dev.platform.board.application.port.out.MembershipSnapshotPort;
import dev.platform.board.application.port.out.ProjectDataPort;
import dev.platform.board.application.port.out.ProjectEventPort;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProjectApplicationServiceTest {
    private final ProjectDataPort data = mock(ProjectDataPort.class);
    private final ProjectApplicationService service = new ProjectApplicationService(
            data, mock(MembershipSnapshotPort.class), mock(ProjectEventPort.class), mock(AccountLookupPort.class));

    @Test
    void memberCannotRenameProject() {
        UUID memberId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-07T00:00:00Z");
        Project project = new Project(1L, "Project", Project.Status.ACTIVE, UUID.randomUUID(), now, now);
        ProjectMembership membership = new ProjectMembership(1L, memberId, ProjectMembership.Role.MEMBER,
                ProjectMembership.Status.ACTIVE, now, null, now);
        when(data.findProjectForUpdate(1L)).thenReturn(Optional.of(project));
        when(data.findMembership(1L, memberId)).thenReturn(Optional.of(membership));

        ProjectRuleViolation violation = assertThrows(ProjectRuleViolation.class,
                () -> service.rename(new dev.platform.board.application.command.ProjectNameCommand(
                        1L, "New name", memberId)));

        assertEquals(403, violation.httpStatus());
        verify(data, never()).saveProject(any());
    }

    @Test
    void missingMembershipVersionIsReportedInsteadOfReturnedAsZero() {
        UUID memberId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-07T00:00:00Z");
        Project project = new Project(1L, "Project", Project.Status.ACTIVE, UUID.randomUUID(), now, now);
        ProjectMembership membership = new ProjectMembership(1L, memberId, ProjectMembership.Role.MEMBER,
                ProjectMembership.Status.ACTIVE, now, null, now);
        when(data.findProject(1L)).thenReturn(Optional.of(project));
        when(data.findMembership(1L, memberId)).thenReturn(Optional.of(membership));
        when(data.findVersion(1L, memberId)).thenReturn(Optional.empty());

        ProjectRuleViolation violation = assertThrows(ProjectRuleViolation.class,
                () -> service.verifyMember(new dev.platform.board.application.query.MemberAccessQuery(1L, memberId)));

        assertEquals("MEMBERSHIP_VERSION_MISSING", violation.code());
        assertEquals(500, violation.httpStatus());
    }
}
