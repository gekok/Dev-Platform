package dev.platform.board.config.filter;

import dev.platform.board.application.port.out.InternalServiceAuthenticationPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {
    private final InternalServiceAuthenticationPort auth;
    private final HandlerExceptionResolver resolver;

    public InternalServiceAuthFilter(InternalServiceAuthenticationPort auth,
                                     @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.auth = auth;
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return !r.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        try {
            auth.verify(req.getHeader("X-Service-Token"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("internal-service", null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
        } catch (RuntimeException e) {
            resolver.resolveException(req, res, null, e);   // đi qua @RestControllerAdvice
            return;
        }
        chain.doFilter(req, res);
    }
}
