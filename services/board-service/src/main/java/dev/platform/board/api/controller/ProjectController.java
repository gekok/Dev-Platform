package dev.platform.board.api.controller;

import dev.platform.board.api.dto.*;
import dev.platform.board.application.command.*;
import dev.platform.board.application.port.in.ProjectUseCasePort;
import dev.platform.board.application.query.*;
import dev.platform.board.domain.aggregate.Invitation;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectUseCasePort projects;

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody CreateProjectRequest request, Principal principal) {
        ProjectView created = projects.create(new CreateProjectCommand(request.name(), actor(principal)));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.project().id()))
                .body(ApiResponse.of(ProjectResponse.from(created.project(), created.myRole())));
    }

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> listMine(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectPage<ProjectView> result = projects.listMine(new ProjectListQuery(actor(principal), page, size));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(result,
                view -> ProjectResponse.from(view.project(), view.myRole()))));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(
            @PathVariable Long projectId, Principal principal) {
        ProjectView project = projects.get(new ProjectQuery(projectId, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(ProjectResponse.from(project.project(), project.myRole())));
    }

    @PatchMapping("/projects/{projectId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> rename(
            @PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest request, Principal principal) {
        ProjectView project = projects.rename(new ProjectNameCommand(projectId, request.name(), actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(ProjectResponse.from(project.project(), project.myRole())));
    }

    @PatchMapping("/projects/{projectId}/status")
    public ResponseEntity<ApiResponse<ProjectResponse>> changeStatus(
            @PathVariable Long projectId,
            @Valid @RequestBody ChangeProjectStatusRequest request,
            Principal principal) {
        Project.Status status;
        try {
            status = Project.Status.valueOf(request.status());
        } catch (IllegalArgumentException exception) {
            throw ProjectRuleViolation.validation("status must be ACTIVE, COMPLETED, or CANCELLED.");
        }
        ProjectView project = projects.changeStatus(
                new ChangeProjectStatusCommand(projectId, status, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(ProjectResponse.from(project.project(), project.myRole())));
    }

    @PostMapping("/projects/{projectId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(
            @PathVariable Long projectId, @Valid @RequestBody InviteMemberRequest request, Principal principal) {
        Invitation invitation = projects.invite(
                new InviteMemberCommand(projectId, request.userId(), actor(principal)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(InvitationResponse.from(invitation)));
    }

    @GetMapping("/projects/{projectId}/invitations")
    public ResponseEntity<ApiResponse<PageResponse<InvitationResponse>>> listProjectInvitations(
            @PathVariable Long projectId,
            Principal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectPage<Invitation> result = projects.listProjectInvitations(
                new InvitationsQuery(projectId, status, actor(principal), page, size));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(result, InvitationResponse::from)));
    }

    @PostMapping("/projects/{projectId}/invitations/{invitationId}/cancel")
    public ResponseEntity<ApiResponse<InvitationResponse>> cancelInvitation(
            @PathVariable Long projectId, @PathVariable Long invitationId, Principal principal) {
        Invitation invitation = projects.cancelInvitation(
                new CancelInvitationCommand(projectId, invitationId, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(InvitationResponse.from(invitation)));
    }

    @GetMapping("/invitations/me")
    public ResponseEntity<ApiResponse<PageResponse<InvitationResponse>>> listMyInvitations(
            Principal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectPage<InvitationInboxItem> result = projects.listMyInvitations(
                new InvitationInboxQuery(status, actor(principal), page, size));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(result,
                item -> InvitationResponse.from(item.invitation(), item.projectName()))));
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<ApiResponse<MembershipResponse>> acceptInvitation(
            @PathVariable Long invitationId, Principal principal) {
        ProjectMembership membership = projects.acceptInvitation(
                new InvitationResponseCommand(invitationId, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(MembershipResponse.from(membership)));
    }

    @PostMapping("/invitations/{invitationId}/reject")
    public ResponseEntity<ApiResponse<InvitationResponse>> rejectInvitation(
            @PathVariable Long invitationId, Principal principal) {
        Invitation invitation = projects.rejectInvitation(
                new InvitationResponseCommand(invitationId, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(InvitationResponse.from(invitation)));
    }

    @GetMapping("/projects/{projectId}/members")
    public ResponseEntity<ApiResponse<PageResponse<MembershipResponse>>> members(
            @PathVariable Long projectId,
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectPage<ProjectMembership> result = projects.listMembers(
                new MembersQuery(projectId, actor(principal), page, size));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(result, MembershipResponse::from)));
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long projectId, @PathVariable UUID userId, Principal principal) {
        projects.removeMember(new RemoveMemberCommand(projectId, userId, actor(principal)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/leave")
    public ResponseEntity<Void> leave(@PathVariable Long projectId, Principal principal) {
        projects.leaveProject(projectId, actor(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/transfer-ownership")
    public ResponseEntity<ApiResponse<MembershipResponse>> transferOwnership(
            @PathVariable Long projectId,
            @Valid @RequestBody TransferOwnershipRequest request,
            Principal principal) {
        ProjectMembership membership = projects.transferOwnership(
                new TransferOwnershipCommand(projectId, request.newOwnerUserId(), actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(MembershipResponse.from(membership)));
    }

    private UUID actor(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw ProjectRuleViolation.unauthorized("Authenticated user identity is required.");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw ProjectRuleViolation.unauthorized("Authenticated user identity is invalid.");
        }
    }
}
