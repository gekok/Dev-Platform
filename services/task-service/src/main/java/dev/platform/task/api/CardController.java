package dev.platform.task.api;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.port.in.AddCommentPort;
import dev.platform.task.application.port.in.AssignCardPort;
import dev.platform.task.application.port.in.CreateCardPort;
import dev.platform.task.application.port.in.GetCardsPort;
import dev.platform.task.application.port.in.MoveCardPort;
import dev.platform.task.application.service.ProjectAuthorizationGuard;
import dev.platform.task.application.service.CardCursor;
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

    @Value("${dev-platform.security.trusted-gateway-identity.enabled:false}")
    private boolean trustedGatewayIdentityEnabled;

    // Tạo thẻ (3.1).
    @PostMapping("/api/v1/projects/{projectId}/boards/{boardId}/cards")
    public ResponseEntity<CardResponse> createCard(
            @PathVariable("projectId") Long projectId,
            @PathVariable("boardId") Long boardId,
            @RequestBody CreateCardRequest request,
            // TODO Gateway phải ghi đè header này từ principal đã xác thực.
            @RequestHeader(value = "X-User-Id") UUID userId) {
        if (request.assigneeId() != null && !trustedGatewayIdentityEnabled) {
            throw new CardOperationUnavailableException();
        }
        authorizationGuard.requireProjectMember(projectId, userId);
        Long assigneeMembershipVersion = request.assigneeId() == null ? null
                : authorizationGuard.requireActiveAssigneeMembership(projectId, request.assigneeId());
        CreateCardCommand cmd = new CreateCardCommand(projectId, boardId, request.columnId(), request.title(),
                request.description(), request.assigneeId(), assigneeMembershipVersion, request.dueDate(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(createCardPort.create(cmd)));
    }

    // Lấy thẻ theo bảng, lọc cột tùy chọn (3.2).
    @GetMapping("/api/v1/projects/{projectId}/boards/{boardId}/cards")
    public ResponseEntity<CardPage> listCards(
            @PathVariable("projectId") Long projectId,
            @PathVariable("boardId") Long boardId,
            @RequestParam(value = "columnId", required = false) Long columnId,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestHeader(value = "X-User-Id") UUID userId) {
        authorizationGuard.requireProjectMember(projectId, userId);
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("limit must be between 1 and 20");
        CardCursor.Anchor anchor = cursor == null ? null : CardCursor.decode(cursor, projectId, boardId, columnId);
        List<CardAggregate> cards = getCardsPort.getByBoard(projectId, boardId, columnId,
                anchor == null ? null : anchor.position(), anchor == null ? null : anchor.id(), limit + 1);
        boolean hasMore = cards.size() > limit;
        List<CardAggregate> page = hasMore ? cards.subList(0, limit) : cards;
        CardAggregate last = hasMore ? page.get(page.size() - 1) : null;
        String next = last == null ? null : CardCursor.encode(projectId, boardId, columnId, last.getPosition(), last.getId());
        return ResponseEntity.ok(new CardPage(page.stream().map(CardResponse::from).toList(), next));
    }

    public record CardPage(List<CardResponse> items, String nextCursor) {}

    // Kéo thẻ qua cột (3.3).
    @PatchMapping("/api/v1/projects/{projectId}/cards/{cardId}/move")
    public ResponseEntity<CardResponse> moveCard(
            @PathVariable("projectId") Long projectId,
            @PathVariable("cardId") Long cardId,
            @RequestBody MoveCardRequest request,
            @RequestHeader(value = "X-User-Id") UUID actorId) {
        authorizationGuard.requireProjectMember(projectId, actorId);
        if (request.toPosition() == null) {
            throw new IllegalArgumentException("toPosition is required");
        }
        MoveCardCommand cmd = new MoveCardCommand(
                projectId, cardId, request.toColumnId(), request.toPosition(), actorId);
        CardAggregate card = moveCardPort.move(cmd);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // Gán người làm (3.4).
    @PatchMapping("/api/v1/projects/{projectId}/cards/{cardId}/assignee")
    public ResponseEntity<CardResponse> assignCard(
            @PathVariable("projectId") Long projectId,
            @PathVariable("cardId") Long cardId,
            @RequestBody AssignCardRequest request,
            @RequestHeader(value = "X-User-Id") UUID actorId) {
        if (!trustedGatewayIdentityEnabled) {
            throw new CardOperationUnavailableException();
        }
        authorizationGuard.requireProjectMember(projectId, actorId);
        Long assigneeMembershipVersion = request.assigneeId() == null ? null
                : authorizationGuard.requireActiveAssigneeMembership(projectId, request.assigneeId());
        AssignCardCommand cmd = new AssignCardCommand(
                projectId, cardId, request.assigneeId(), assigneeMembershipVersion, actorId);
        CardAggregate card = assignCardPort.assign(cmd);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // Bình luận (3.5).
    @GetMapping("/api/v1/projects/{projectId}/cards/{cardId}/comments")
    public ResponseEntity<List<CommentResponse>> listComments(
            @PathVariable("projectId") Long projectId, @PathVariable("cardId") Long cardId,
            @RequestHeader(value = "X-User-Id") UUID userId) {
        authorizationGuard.requireProjectMember(projectId, userId);
        return ResponseEntity.ok(addCommentPort.getComments(projectId, cardId).stream()
                .map(comment -> CommentResponse.from(cardId, comment)).toList());
    }

    @PostMapping("/api/v1/projects/{projectId}/cards/{cardId}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable("projectId") Long projectId,
            @PathVariable("cardId") Long cardId,
            @RequestBody AddCommentRequest request,
            @RequestHeader(value = "X-User-Id") UUID authorId) {
        authorizationGuard.requireProjectMember(projectId, authorId);
        AddCommentCommand cmd = new AddCommentCommand(
                projectId, cardId, authorId, request.content(), request.mentionedUserIds());
        CommentValue comment = addCommentPort.addComment(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(cardId, comment));
    }
}
