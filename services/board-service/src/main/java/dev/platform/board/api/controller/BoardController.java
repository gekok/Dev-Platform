package dev.platform.board.api.controller;

import dev.platform.board.api.dto.*;
import dev.platform.board.application.command.*;
import dev.platform.board.application.port.in.BoardUseCasePort;
import dev.platform.board.application.query.*;
import dev.platform.board.domain.aggregate.Board;
import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BoardController {
    private final BoardUseCasePort boards;

    @PostMapping("/projects/{projectId}/boards")
    public ResponseEntity<ApiResponse<BoardResponse>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateBoardRequest request,
            Principal principal) {
        Board created = boards.create(new CreateProjectBoardCommand(
                projectId, request.name(), request.boardKey(),
                request.createDefaultColumns() == null || request.createDefaultColumns(), actor(principal)));
        return ResponseEntity.created(URI.create("/api/v1/boards/" + created.id()))
                .body(ApiResponse.of(BoardResponse.from(created, true, "OWNER")));
    }

    @GetMapping("/projects/{projectId}/boards")
    public ResponseEntity<ApiResponse<PageResponse<BoardSummaryResponse>>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Principal principal) {
        BoardPage<BoardSummary> result = boards.list(
                new BoardsQuery(projectId, status, page, size, sort, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(result,
                summary -> BoardSummaryResponse.from(summary.board(), summary.columnCount()))));
    }

    @GetMapping("/boards/{boardId}")
    public ResponseEntity<ApiResponse<BoardResponse>> get(
            @PathVariable Long boardId, Principal principal) {
        BoardView view = boards.get(new BoardQuery(boardId, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(
                BoardResponse.from(view.board(), view.writable(), view.myRole())));
    }

    @PatchMapping("/boards/{boardId}")
    public ResponseEntity<ApiResponse<BoardResponse>> rename(
            @PathVariable Long boardId,
            @Valid @RequestBody UpdateBoardRequest request,
            Principal principal) {
        Board updated = boards.rename(new BoardNameCommand(
                boardId, request.name(), request.boardKey(), actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(BoardResponse.from(updated, true, "OWNER")));
    }

    @PatchMapping("/boards/{boardId}/status")
    public ResponseEntity<ApiResponse<BoardResponse>> changeStatus(
            @PathVariable Long boardId,
            @Valid @RequestBody ChangeBoardStatusRequest request,
            Principal principal) {
        Board.Status status;
        try {
            status = Board.Status.valueOf(request.status());
        } catch (IllegalArgumentException exception) {
            throw ProjectRuleViolation.validation("status must be ACTIVE, COMPLETED, or CANCELLED.");
        }
        Board updated = boards.changeStatus(new BoardStatusCommand(boardId, status, actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(BoardResponse.from(updated,
                updated.status() == Board.Status.ACTIVE, "OWNER")));
    }

    @PostMapping("/boards/{boardId}/columns")
    public ResponseEntity<ApiResponse<AddBoardColumnResponse>> addColumn(
            @PathVariable Long boardId,
            @Valid @RequestBody CreateColumnRequest request,
            Principal principal) {
        Board updated = boards.addColumn(new AddBoardColumnCommand(
                boardId, request.name(), request.position(), actor(principal)));
        int addedPosition = request.position() == null ? updated.columns().size() - 1 : request.position();
        Board.Column added = updated.columns().get(addedPosition);
        ColumnResponse response = ColumnResponse.from(added);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                new AddBoardColumnResponse(response,
                        updated.columns().stream().map(ColumnResponse::from).toList())));
    }

    @PutMapping("/boards/{boardId}/columns/order")
    public ResponseEntity<ApiResponse<java.util.List<ColumnResponse>>> reorderColumns(
            @PathVariable Long boardId,
            @Valid @RequestBody ReorderBoardColumnsRequest request,
            Principal principal) {
        Board updated = boards.reorderColumns(new ReorderBoardColumnsCommand(
                boardId, request.columnIds(), actor(principal)));
        return ResponseEntity.ok(ApiResponse.of(
                updated.columns().stream().map(ColumnResponse::from).toList()));
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
