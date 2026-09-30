package dev.platform.board.api;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import dev.platform.board.api.dto.AddColumnRequest;
import dev.platform.board.api.dto.BoardResponse;
import dev.platform.board.api.dto.ColumnResponse;
import dev.platform.board.api.dto.CreateBoardRequest;
import dev.platform.board.api.dto.ReorderColumnsRequest;
import dev.platform.board.application.command.AddColumnCommand;
import dev.platform.board.application.command.CreateBoardCommand;
import dev.platform.board.application.command.ReorderColumnsCommand;
import dev.platform.board.application.port.in.AddColumnPort;
import dev.platform.board.application.port.in.CreateBoardPort;
import dev.platform.board.application.port.in.GetBoardPort;
import dev.platform.board.application.port.in.ReorderColumnsPort;
import dev.platform.board.domain.aggregate.BoardAggregate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// REST bảng Kanban (kanban-api mục 2).
@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final CreateBoardPort createBoardPort;
    private final AddColumnPort addColumnPort;
    private final ReorderColumnsPort reorderColumnsPort;
    private final GetBoardPort getBoardPort;

    // TODO kỳ 1: parse JWT sub, hiện stub lấy X-User-Id.
    private UUID currentUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Thieu X-User-Id");
        }
        return userId;
    }

    // 2.1 Tạo bảng -> 201.
    @PostMapping
    public ResponseEntity<BoardResponse> createBoard(
            @Valid @RequestBody CreateBoardRequest req,
            @RequestHeader("X-User-Id") UUID userId) {
        BoardAggregate board = createBoardPort.create(new CreateBoardCommand(
                req.name(), req.key(), currentUserId(userId), req.columns()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BoardResponse.from(board));
    }

    // 2.2 Danh sách bảng của tôi -> 200.
    @GetMapping
    public ResponseEntity<List<BoardResponse>> listMyBoards(
            @RequestHeader("X-User-Id") UUID userId) {
        List<BoardResponse> boards = getBoardPort.listMine(currentUserId(userId)).stream()
                .map(BoardResponse::from)
                .toList();
        return ResponseEntity.ok(boards);
    }

    // 2.3 Chi tiết bảng kèm cột -> 200.
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardResponse> getBoard(
            @PathVariable Long boardId,
            @RequestHeader("X-User-Id") UUID userId) {
        BoardAggregate board = getBoardPort.getById(boardId, currentUserId(userId))
                .orElseThrow(() -> new NoSuchElementException("Khong thay board " + boardId));
        return ResponseEntity.ok(BoardResponse.from(board));
    }

    // 2.4 Thêm cột -> 201.
    @PostMapping("/{boardId}/columns")
    public ResponseEntity<ColumnResponse> addColumn(
            @PathVariable Long boardId,
            @Valid @RequestBody AddColumnRequest req,
            @RequestHeader("X-User-Id") UUID userId) {
        currentUserId(userId); // Giữ để check 401/403 kỳ sau.
        BoardAggregate board = addColumnPort.addColumn(new AddColumnCommand(boardId, req.name()));
        List<BoardAggregate.BoardColumnValue> columns = board.getColumns();
        BoardAggregate.BoardColumnValue last = columns.get(columns.size() - 1);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ColumnResponse(last.id(), last.name(), last.position()));
    }

    // 2.5 Đổi thứ tự cột -> 200.
    @PatchMapping("/{boardId}/columns/order")
    public ResponseEntity<List<ColumnResponse>> reorderColumns(
            @PathVariable Long boardId,
            @Valid @RequestBody ReorderColumnsRequest req,
            @RequestHeader("X-User-Id") UUID userId) {
        currentUserId(userId); // Giữ để check 401/403 kỳ sau.
        BoardAggregate board = reorderColumnsPort
                .reorder(new ReorderColumnsCommand(boardId, req.orderedColumnIds()));
        List<ColumnResponse> columns = board.getColumns().stream()
                .map(c -> new ColumnResponse(c.id(), c.name(), c.position()))
                .toList();
        return ResponseEntity.ok(columns);
    }
}
