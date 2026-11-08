package dev.platform.board.api.controller;

import dev.platform.board.application.port.in.BoardUseCasePort;
import dev.platform.board.application.port.out.InternalServiceAuthenticationPort;
import dev.platform.board.application.query.BoardAccess;
import dev.platform.board.application.query.InternalBoardQuery;
import dev.platform.board.api.dto.InternalBoardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/boards")
@RequiredArgsConstructor
public class InternalBoardController {
    private final BoardUseCasePort boards;
    private final InternalServiceAuthenticationPort serviceAuthentication;

    @GetMapping("/{boardId}")
    public ResponseEntity<InternalBoardResponse> verifyBoard(
            @PathVariable Long boardId,
            @RequestHeader(name = "X-Service-Token", required = false) String serviceToken) {
        serviceAuthentication.verify(serviceToken);
        BoardAccess access = boards.verifyBoard(new InternalBoardQuery(boardId));
        return ResponseEntity.ok(InternalBoardResponse.from(access));
    }
}
