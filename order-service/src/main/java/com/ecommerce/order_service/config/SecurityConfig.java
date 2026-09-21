package com.ecommerce.order_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {
    private static java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities(Object roles) {
        if (!(roles instanceof java.util.List<?> list)) return java.util.List.of();
        return list.stream().filter(String.class::isInstance).map(Object::toString)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http, @Value("${security.jwt.secret}") String secret, @Value("${internal.service-key:local-development-service-key-change-me}") String serviceKey) throws Exception {
        var parser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build();
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain chain) throws ServletException, IOException {
                if ((request.getServletPath().startsWith("/internal/") || request.getRequestURI().startsWith(request.getContextPath() + "/internal/"))) {
                    String supplied = request.getHeader("X-Service-Key");
                    if (serviceKey.isBlank() || supplied == null || !java.security.MessageDigest.isEqual(
                            serviceKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                        response.sendError(401); return;
                    }
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new UsernamePasswordAuthenticationToken("order-service", null, List.of()));
                    SecurityContextHolder.setContext(context);
                    chain.doFilter(request, response);
                    return;
                }
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    try {
                        var claims = parser.parseSignedClaims(header.substring(7)).getPayload();
                        Object id = claims.get("userId");
                        if (!(id instanceof Number) || claims.getExpiration() == null || claims.getSubject() == null) {
                            throw new IllegalArgumentException("Invalid access token");
                        }
                        long userId = Long.parseLong(id.toString());
                        if (userId <= 0) throw new IllegalArgumentException("Invalid user ID");
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, authorities(claims.get("roles"))));
                        SecurityContextHolder.setContext(context);
                    } catch (RuntimeException ex) {
                        SecurityContextHolder.clearContext();
                        response.sendError(401, "Invalid or expired access token");
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        };
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/actuator/health/**", "/error", "/api/payments/razorpay/webhook").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reviews/products/**").permitAll().anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((r, s, ex) -> s.sendError(401)))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
