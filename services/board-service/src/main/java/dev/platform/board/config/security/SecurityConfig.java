package dev.platform.board.config.security;

import dev.platform.board.config.filter.GatewayAuthFilter;
import dev.platform.board.config.filter.InternalServiceAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    GatewayAuthFilter gatewayAuthFilter,
                                    InternalServiceAuthFilter internalAuthFilter) throws Exception {
        http
                .csrf(c -> c.disable())                       // API stateless, không dùng cookie
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "FORBIDDEN")))
                .addFilterBefore(gatewayAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Tránh filter bị đăng ký thêm một lần ở tầng servlet
    @Bean
    FilterRegistrationBean<GatewayAuthFilter> disableGatewayAuthAutoReg(GatewayAuthFilter f) {
        var reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    FilterRegistrationBean<InternalServiceAuthFilter> disableInternalAuthAutoReg(InternalServiceAuthFilter f) {
        var reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false);
        return reg;
    }

    private void writeError(HttpServletResponse res, int status, String code) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("""
            {"error":{"code":"%s","message":"%s"}}""".formatted(code, code));
    }
}
