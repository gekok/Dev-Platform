package dev.platform.task.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.platform.task.api.dto.AddCommentRequest;
import dev.platform.task.api.dto.AssignCardRequest;
import dev.platform.task.api.dto.CardResponse;
import dev.platform.task.api.dto.CommentResponse;
import dev.platform.task.api.dto.CreateCardRequest;
import dev.platform.task.api.dto.MoveCardRequest;
import dev.platform.task.application.command.AddCommentCommand;
import dev.platform.task.application.command.AssignCardCommand;
import dev.platform.task.application.command.CreateCardCommand;
import dev.platform.task.application.command.MoveCardCommand;
import dev.platform.task.application.port.in.AddCommentPort;
import dev.platform.task.application.port.in.AssignCardPort;
import dev.platform.task.application.port.in.CreateCardPort;
import dev.platform.task.application.port.in.GetCardsPort;
import dev.platform.task.application.port.in.MoveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;
import lombok.RequiredArgsConstructor;

/**
 * Card REST theo docs/api/kanban-api.md mục 3.
 * Kỳ 1 viết service cài đặt 5 port in + bắn event WS.
 */
// Kênh WS /ws/boards/{boardId} nằm ở infrastructure/websocket riêng.
// Không đặt WS trong @RestController vì không giữ kết nối socket được.
@RestController
@RequiredArgsConstructor
public class CardController {

    private final CreateCardPort createCardPort;
    private final GetCardsPort getCardsPort;
    private final MoveCardPort moveCardPort;
    private final AssignCardPort assignCardPort;
    private final AddCommentPort addCommentPort;

    // Tạo thẻ (3.1).
    @PostMapping("/api/v1/boards/{boardId}/cards")
    public ResponseEntity<CardResponse> createCard(
            @PathVariable Long boardId,
            @RequestBody CreateCardRequest request,
            // TODO kỳ 1: lấy userId từ JWT sub, hiện đọc header stub.
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        CreateCardCommand cmd = new CreateCardCommand(
                boardId,
                request.columnId(),
                request.title(),
                request.description(),
                request.assigneeId(),
                request.dueDate(),
                userId);
        CardAggregate card = createCardPort.create(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    // Lấy thẻ theo bảng, lọc cột tùy chọn (3.2).
    @GetMapping("/api/v1/boards/{boardId}/cards")
    public ResponseEntity<List<CardResponse>> listCards(
            @PathVariable Long boardId,
            @RequestParam(required = false) Long columnId) {
        List<CardAggregate> cards = getCardsPort.getByBoard(boardId, columnId);
        return ResponseEntity.ok(cards.stream().map(CardResponse::from).toList());
    }

    // Kéo thẻ qua cột (3.3).
    @PatchMapping("/api/v1/cards/{cardId}/move")
    public ResponseEntity<CardResponse> moveCard(
            @PathVariable Long cardId,
            @RequestBody MoveCardRequest request) {
        MoveCardCommand cmd = new MoveCardCommand(cardId, request.toColumnId(), request.toPosition());
        CardAggregate card = moveCardPort.move(cmd);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // Gán người làm (3.4).
    @PatchMapping("/api/v1/cards/{cardId}/assignee")
    public ResponseEntity<CardResponse> assignCard(
            @PathVariable Long cardId,
            @RequestBody AssignCardRequest request,
            // TODO kỳ 1: lấy actorId từ JWT sub, hiện đọc header stub.
            @RequestHeader(value = "X-User-Id", required = false) UUID actorId) {
        AssignCardCommand cmd = new AssignCardCommand(cardId, request.assigneeId(), actorId);
        CardAggregate card = assignCardPort.assign(cmd);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // Bình luận (3.5).
    @PostMapping("/api/v1/cards/{cardId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long cardId,
            @RequestBody AddCommentRequest request,
            // TODO kỳ 1: lấy authorId từ JWT sub, hiện đọc header stub.
            @RequestHeader(value = "X-User-Id", required = false) UUID authorId) {
        AddCommentCommand cmd = new AddCommentCommand(
                cardId, authorId, request.content(), request.mentionedUserIds());
        CommentValue comment = addCommentPort.addComment(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(cardId, comment));
    }
}
