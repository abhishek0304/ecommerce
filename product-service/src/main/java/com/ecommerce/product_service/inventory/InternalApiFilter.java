package com.ecommerce.product_service.inventory;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalApiFilter extends OncePerRequestFilter {
    private final byte[] key;
    public InternalApiFilter(@Value("${internal.service-key:local-development-service-key-change-me}") String key) {
        if (key.isBlank()) throw new IllegalArgumentException("Internal service key cannot be blank");
        this.key = key.getBytes(StandardCharsets.UTF_8);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ((request.getServletPath().startsWith("/internal/") || request.getRequestURI().startsWith(request.getContextPath() + "/internal/"))) {
            String supplied = request.getHeader("X-Service-Key");
            if (supplied == null || !MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8))) {
                response.sendError(401); return;
            }
        }
        chain.doFilter(request, response);
    }
}
