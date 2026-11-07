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
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.infrastructure.security.JwtTokenAdapter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

// Biên REST Auth/User: nhận DTO, gọi port in, không nhận userId do client tự gửi.
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final RegisterUserPort registerPort;
    private final LoginUserPort loginPort;
    private final GetCurrentUserPort mePort;
    private final TokenPort tokenPort;

    public UserController(
            @Autowired(required = false) RegisterUserPort registerPort,
            LoginUserPort loginPort,
            @Autowired(required = false) GetCurrentUserPort mePort,
            @Autowired(required = false) TokenPort tokenPort) {
        this.registerPort = registerPort;
        this.loginPort = loginPort;
        this.mePort = mePort;
        this.tokenPort = tokenPort;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        if (registerPort == null) {
            throw new UnsupportedOperationException(
                    "Public registration has been deprecated per decision #7. Accounts must be provisioned by System Admin.");
        }
        UserResponse body = UserResponse.from(
                registerPort.register(new RegisterCommand(req.email(), req.password(), req.fullName())));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        String token = loginPort.login(new LoginCommand(req.email(), req.password()));
        return ResponseEntity.ok(TokenResponse.of(token));
    }

    /**
     * Endpoint công khai cung cấp Public Key RSA256 cho Gateway và các consumers để xác thực token.
     */
    @GetMapping("/auth/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        if (tokenPort instanceof JwtTokenAdapter jwtAdapter) {
            return ResponseEntity.ok(Map.of(
                    "algorithm", "RSA256",
                    "publicKey", jwtAdapter.getPublicKeyPem()
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/users/me")
    public ResponseEntity<UserResponse> me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (mePort == null) {
            throw new UnsupportedOperationException("GetCurrentUserPort is not implemented yet in this version");
        }

        UUID userId;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7).trim();
            userId = tokenPort.parseAndValidate(token);
        } else {
            // Khi không có Bearer token hợp lệ, từ chối theo contract bảo mật
            throw new AuthenticationException("Missing or invalid Authorization header");
        }

        UserResponse body = UserResponse.from(mePort.me(userId));
        return ResponseEntity.ok(body);
    }
}
