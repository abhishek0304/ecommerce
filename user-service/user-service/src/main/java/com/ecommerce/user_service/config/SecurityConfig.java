package com.ecommerce.user_service.config;

import org.springframework.web.filter.OncePerRequestFilter;
import com.ecommerce.user_service.util.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.io.*;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtService jwt;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, @org.springframework.beans.factory.annotation.Value("${internal.service-key:local-development-service-key-change-me}") String serviceKey) throws Exception {
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a -> a.requestMatchers("/api/auth/logout", "/api/auth/logout-all").authenticated().requestMatchers("/api/auth/**", "/api/users/register", "/auth/**", "/forgot-password", "/actuator/**", "/hello").permitAll().requestMatchers("/api/users/*").authenticated().anyRequest().authenticated()).exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> response.sendError(401))).addFilterBefore(new JwtFilter(jwt,serviceKey), UsernamePasswordAuthenticationFilter.class).build();
    }


    static class JwtFilter extends OncePerRequestFilter {
        private final JwtService jwt;
        private final String serviceKey;

        JwtFilter(JwtService jwt,String serviceKey) {
            this.jwt = jwt;
            this.serviceKey=serviceKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c) throws ServletException, IOException {
            if(r.getRequestURI().startsWith(r.getContextPath()+"/internal/")) {
                String supplied=r.getHeader("X-Service-Key");
                if(serviceKey.isBlank() || supplied==null || !java.security.MessageDigest.isEqual(serviceKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {s.sendError(401);return;}
                var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken("notification-service",null,java.util.List.of()));
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);c.doFilter(r,s);return;
            }
            String h = r.getHeader(HttpHeaders.AUTHORIZATION);
            if (h != null && h.startsWith("Bearer ")) {
                try {
                    var claims = jwt.claims(h.substring(7));
                    var roles = ((java.util.List<?>) claims.get("roles")).stream().map(Object::toString).map(SimpleGrantedAuthority::new).toList();
                    var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, roles);
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (Exception ignored) {
                }
            }
            c.doFilter(r, s);
        }
    }

    public SecurityConfig(final JwtService jwt) {
        this.jwt = jwt;
    }
}
