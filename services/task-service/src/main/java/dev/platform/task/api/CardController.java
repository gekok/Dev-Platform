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
import dev.platform.task.application.service.ProjectAuthorizationGuard;
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
    private final ProjectAuthorizationGuard authorizationGuard;

    // Tạo thẻ (3.1).
    @PostMapping("/api/v1/projects/{projectId}/boards/{boardId}/cards")
    public ResponseEntity<CardResponse> createCard(
            @PathVariable Long projectId,
            @PathVariable Long boardId,
            @RequestBody CreateCardRequest request,
            // TODO Gateway phải ghi đè header này từ principal đã xác thực.
            @RequestHeader(value = "X-User-Id") UUID userId) {
        authorizationGuard.requireProjectMember(projectId, userId);
        Long assigneeMembershipVersion = request.assigneeId() == null ? null
                : authorizationGuard.requireActiveAssigneeMembership(projectId, request.assigneeId());
        CreateCardCommand cmd = new CreateCardCommand(
                projectId,
                boardId,
                request.columnId(),
                request.title(),
                request.description(),
                request.assigneeId(),
                assigneeMembershipVersion,
                request.dueDate(),
                userId);
        CardAggregate card = createCardPort.create(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    // Lấy thẻ theo bảng, lọc cột tùy chọn (3.2).
    @GetMapping("/api/v1/projects/{projectId}/boards/{boardId}/cards")
    public ResponseEntity<List<CardResponse>> listCards(
            @PathVariable Long projectId,
            @PathVariable Long boardId,
            @RequestParam(required = false) Long columnId,
            @RequestHeader(value = "X-User-Id") UUID userId) {
        authorizationGuard.requireProjectMember(projectId, userId);
        List<CardAggregate> cards = getCardsPort.getByBoard(projectId, boardId, columnId);
        return ResponseEntity.ok(cards.stream().map(CardResponse::from).toList());
    }

    // Kéo thẻ qua cột (3.3).
    @PatchMapping("/api/v1/projects/{projectId}/cards/{cardId}/move")
    public ResponseEntity<CardResponse> moveCard(
            @PathVariable Long projectId,
            @PathVariable Long cardId,
            @RequestBody MoveCardRequest request,
            @RequestHeader(value = "X-User-Id") UUID actorId) {
        authorizationGuard.requireProjectMember(projectId, actorId);
        MoveCardCommand cmd = new MoveCardCommand(
                projectId, cardId, request.toColumnId(), request.toPosition(), actorId);
        CardAggregate card = moveCardPort.move(cmd);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // Gán người làm (3.4).
    @PatchMapping("/api/v1/projects/{projectId}/cards/{cardId}/assignee")
    public ResponseEntity<CardResponse> assignCard(
            @PathVariable Long projectId,
            @PathVariable Long cardId,
            @RequestBody AssignCardRequest request,
            @RequestHeader(value = "X-User-Id") UUID actorId) {
        authorizationGuard.requireProjectMember(projectId, actorId);
        Long assigneeMembershipVersion = request.assigneeId() == null ? null
                : authorizationGuard.requireActiveAssigneeMembership(projectId, request.assigneeId());
        AssignCardCommand cmd = new AssignCardCommand(
                projectId, cardId, request.assigneeId(), assigneeMembershipVersion, actorId);
        CardAggregate card = assignCardPort.assign(cmd);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // Bình luận (3.5).
    @PostMapping("/api/v1/projects/{projectId}/cards/{cardId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long projectId,
            @PathVariable Long cardId,
            @RequestBody AddCommentRequest request,
            @RequestHeader(value = "X-User-Id") UUID authorId) {
        authorizationGuard.requireProjectMember(projectId, authorId);
        AddCommentCommand cmd = new AddCommentCommand(
                projectId, cardId, authorId, request.content(), request.mentionedUserIds());
        CommentValue comment = addCommentPort.addComment(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(cardId, comment));
    }
}
