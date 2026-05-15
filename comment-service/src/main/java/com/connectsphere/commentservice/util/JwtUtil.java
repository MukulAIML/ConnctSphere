package com.connectsphere.commentservice.util;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtUtil {

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			log.warn("Invalid JWT: {}", e.getMessage());
			return false;
		}
	}

	public int extractUserId(String token) {
		Claims claims = parseClaims(token);
		Object id = claims.get("userId");
		if (id instanceof Integer) {
			return (Integer) id;
		}
		if (id instanceof Long) {
			return ((Long) id).intValue();
		}
		if (id instanceof String) {
			return Integer.parseInt((String) id);
		}
		return Integer.parseInt(claims.getSubject());
	}

	public String extractEmail(String token) {
		return parseClaims(token).getSubject();
	}

	public String extractRole(String token) {
		return parseClaims(token).get("role", String.class);
	}

	private Claims parseClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
				.build().parseClaimsJws(token).getBody();
	}
}
