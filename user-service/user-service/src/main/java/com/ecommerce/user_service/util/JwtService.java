package com.ecommerce.user_service.util;

import com.ecommerce.user_service.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class JwtService {
	private final SecretKey key;
	private final long accessMinutes;

	public JwtService(
			@Value("${security.jwt.secret:change-this-development-secret-key-to-at-least-32-bytes}") String secret,
			@Value("${security.jwt.access-token-minutes:15}") long accessMinutes) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessMinutes = accessMinutes;
	}

	public String accessToken(User u) {
		return Jwts.builder().subject(u.getEmail()).claim("userId", u.getId())
				.claim("roles", u.getRoles().stream().map(r -> r.getName().name()).toList()).issuedAt(new Date())
				.expiration(Date.from(Instant.now().plus(Duration.ofMinutes(accessMinutes)))).signWith(key).compact();
	}

	public Claims claims(String token) {
		return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
	}
}
