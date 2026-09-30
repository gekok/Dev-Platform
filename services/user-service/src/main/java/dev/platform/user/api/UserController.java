package dev.platform.user.api;

import dev.platform.user.api.dto.LoginRequest;
import dev.platform.user.api.dto.RegisterRequest;
import dev.platform.user.api.dto.TokenResponse;
import dev.platform.user.api.dto.UserResponse;
import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.command.RegisterCommand;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.in.RegisterUserPort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Biên REST Auth/User: nhận DTO, gọi port in, không nhận userId trong body.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final RegisterUserPort registerPort;
    private final LoginUserPort loginPort;
    private final GetCurrentUserPort mePort;

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        UserResponse body = UserResponse.from(
                registerPort.register(new RegisterCommand(req.email(), req.password(), req.fullName())));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        String token = loginPort.login(new LoginCommand(req.email(), req.password()));
        return ResponseEntity.ok(TokenResponse.of(token));
    }

    @GetMapping("/users/me")
    public ResponseEntity<UserResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        // TODO kỳ 1: parse JWT sub từ Authorization; hiện lấy X-User-Id để test.
        if (xUserId == null || xUserId.isBlank()) {
            throw new IllegalArgumentException("Thieu X-User-Id (stub cho JWT sub)");
        }
        UserResponse body = UserResponse.from(mePort.me(UUID.fromString(xUserId)));
        return ResponseEntity.ok(body);
    }
}
