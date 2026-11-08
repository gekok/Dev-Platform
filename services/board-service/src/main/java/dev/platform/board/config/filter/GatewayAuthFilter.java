package dev.platform.board.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final long MAX_SKEW_MS = 30_000;
    private final byte[] secret;

    public GatewayAuthFilter(@Value("${security.gateway.hmac-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return p.startsWith("/internal/") || p.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String userId = req.getHeader("X-User-Id");
        String ts     = req.getHeader("X-Gateway-Timestamp");
        String sig    = req.getHeader("X-Gateway-Signature");

        if (userId != null && ts != null && sig != null && valid(userId, ts, sig)) {
            var auth = new UsernamePasswordAuthenticationToken(
                    UUID.fromString(userId), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        // Không hợp lệ: không set Authentication, để Spring trả 401 qua authenticationEntryPoint
        try { chain.doFilter(req, res); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private boolean valid(String userId, String ts, String sig) {
        try {
            if (Math.abs(System.currentTimeMillis() - Long.parseLong(ts)) > MAX_SKEW_MS) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((userId + "." + ts).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(HexFormat.of().formatHex(expected).getBytes(), sig.getBytes());
        } catch (Exception e) {
            return false;
        }
    }
}
