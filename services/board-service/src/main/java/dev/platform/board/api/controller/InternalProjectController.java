package dev.platform.board.api.controller;

import dev.platform.board.api.dto.InternalMembershipResponse;
import dev.platform.board.application.port.in.ProjectUseCasePort;
import dev.platform.board.application.port.out.InternalServiceAuthenticationPort;
import dev.platform.board.application.query.MemberAccessQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/projects")
@RequiredArgsConstructor
public class InternalProjectController {
    private final ProjectUseCasePort projects;
    private final InternalServiceAuthenticationPort serviceAuthentication;

    @GetMapping("/{projectId}/members/{userId}")
    public ResponseEntity<InternalMembershipResponse> verifyMembership(
            @PathVariable Long projectId,
            @PathVariable UUID userId,
            @RequestHeader(name = "X-Service-Token", required = false) String serviceToken) {
        serviceAuthentication.verify(serviceToken);
        return ResponseEntity.ok(InternalMembershipResponse.from(
                projects.verifyMember(new MemberAccessQuery(projectId, userId))));
    }
}
