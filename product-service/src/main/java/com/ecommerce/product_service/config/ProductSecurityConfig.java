package com.ecommerce.product_service.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class ProductSecurityConfig {
    @Bean
    SecurityFilterChain productSecurity(HttpSecurity http, @Value("${security.jwt.secret}") String secret) throws Exception {
        var parser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).build();
        var jwtFilter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    try {
                        var claims = parser.parseSignedClaims(header.substring(7)).getPayload();
                        if (claims.getExpiration() == null || claims.getSubject() == null || claims.getSubject().isBlank()
                                || !(claims.get("userId") instanceof Number id) || Long.parseLong(id.toString()) <= 0)
                            throw new IllegalArgumentException("Invalid access token");
                        List<SimpleGrantedAuthority> roles = claims.get("roles") instanceof List<?> values
                                ? values.stream().filter(String.class::isInstance).map(Object::toString)
                                        .map(SimpleGrantedAuthority::new).toList() : List.of();
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(new UsernamePasswordAuthenticationToken(claims.getSubject(), null, roles));
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
        // Internal inventory endpoints retain their separate X-Service-Key filter.
        return http.securityMatcher("/api/v1/products", "/api/v1/products/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/products/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/products", "/api/v1/products/**").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) -> response.sendError(401))
                        .accessDeniedHandler((request, response, ex) -> response.sendError(403)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
