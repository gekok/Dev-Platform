package dev.platform.gateway.realtime;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
public class WebSocketTicketController {

    private final WebSocketTicketService tickets;

    public WebSocketTicketController(WebSocketTicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/api/v1/realtime/boards/{boardId}/ws-ticket")
    public Mono<ResponseEntity<TicketResponse>> issue(
            @PathVariable Long boardId,
            @RequestHeader(name = "X-User-Id", required = false) String verifiedUserId) {
        if (boardId == null || boardId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Board ID must be positive");
        }

        UUID accountId;
        try {
            accountId = UUID.fromString(verifiedUserId);
            if (!accountId.toString().equalsIgnoreCase(verifiedUserId)) {
                throw new IllegalArgumentException("Account ID must be a UUID");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verified Gateway identity is required");
        }

        return tickets.issue(accountId, boardId)
                .map(ticket -> ResponseEntity.status(HttpStatus.CREATED)
                        .cacheControl(CacheControl.noStore())
                        .body(new TicketResponse(ticket.ticket(), ticket.expiresInSeconds())))
                .onErrorMap(exception -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "WebSocket ticket storage is unavailable",
                        exception));
    }

    public record TicketResponse(String ticket, long expiresInSeconds) {
    }
}
