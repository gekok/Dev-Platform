package dev.platform.board.api.controller;

import dev.platform.board.api.dto.ApiResponse;
import dev.platform.board.api.dto.ColumnResponse;
import dev.platform.board.api.dto.RenameColumnRequest;
import dev.platform.board.application.command.DeleteBoardColumnCommand;
import dev.platform.board.application.command.RenameBoardColumnCommand;
import dev.platform.board.application.port.in.BoardUseCasePort;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/columns")
@RequiredArgsConstructor
public class BoardColumnController {
    private final BoardUseCasePort boards;

    @PatchMapping("/{columnId}")
    public ResponseEntity<ApiResponse<ColumnResponse>> rename(
            @PathVariable Long columnId,
            @Valid @RequestBody RenameColumnRequest request,
            Principal principal) {
        Board board = boards.renameColumn(
                new RenameBoardColumnCommand(columnId, request.name(), actor(principal)));
        Board.Column column = board.columns().stream()
                .filter(item -> item.id().equals(columnId))
                .findFirst()
                .orElseThrow(() -> ProjectRuleViolation.notFound("Column not found."));
        return ResponseEntity.ok(ApiResponse.of(ColumnResponse.from(column)));
    }

    @DeleteMapping("/{columnId}")
    public ResponseEntity<Void> delete(@PathVariable Long columnId, Principal principal) {
        boards.deleteColumn(new DeleteBoardColumnCommand(columnId, actor(principal)));
        return ResponseEntity.noContent().build();
    }

    private UUID actor(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw dev.platform.board.domain.aggregate.ProjectRuleViolation.unauthorized(
                    "Authenticated user identity is required.");
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw dev.platform.board.domain.aggregate.ProjectRuleViolation.unauthorized(
                    "Authenticated user identity is invalid.");
        }
    }
}
