package dev.platform.user.api;

import dev.platform.user.api.dto.LoginRequest;
import dev.platform.user.api.dto.TokenResponse;
import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.infrastructure.security.JwtTokenAdapter;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final LoginUserPort loginUserPort;
  private final TokenPort tokenPort;

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
    String token = loginUserPort.login(new LoginCommand(req.email(), req.password()));
    return ResponseEntity.ok(TokenResponse.of(token));
  }

  @GetMapping("/public-key")
  public ResponseEntity<Map<String, String>> getPublicKey() {
    if (tokenPort instanceof JwtTokenAdapter jwt) {
      return ResponseEntity.ok(Map.of(
          "algorithm", "RSA256",
          "publicKey", jwt.getPublicKeyPem()
      ));
    }
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }

  @PostMapping("/register")
  public ResponseEntity<Void> register() {
    throw new UnsupportedOperationException(
        "Public registration has been deprecated per decision #7. Accounts must be provisioned by System Admin.");
  }
}
