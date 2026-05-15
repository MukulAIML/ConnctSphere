package com.connectsphere.likeservice.util;

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

	@Value("${jwt.secret}")
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

	/**
	 * Extracts userId from the JWT.
	 * Tries "userId" first, then common alternatives ("id", "user_id").
	 * Handles Integer, Long, and String-encoded numeric values.
	 * Returns 0 only if no recognisable claim is found (treated as unauthenticated).
	 */
	public int extractUserId(String token) {
		Claims claims = parseClaims(token);

		// Try every plausible claim key
		for (String key : new String[]{"userId", "id", "user_id", "uid"}) {
			Object val = claims.get(key);
			if (val != null) {
				return toInt(val);
			}
		}

		// Last resort: parse the JWT subject (sub) as a numeric userId
		String subject = claims.getSubject();
		if (subject != null) {
			try {
				return Integer.parseInt(subject);
			} catch (NumberFormatException ignored) {
				// subject is an email or username, not a userId — skip
			}
		}

		log.warn("Could not extract userId from JWT claims: {}", claims);
		return 0;
	}

	public String extractEmail(String token) {
		return parseClaims(token).getSubject();
	}

	public String extractRole(String token) {
		return parseClaims(token).get("role", String.class);
	}

	private int toInt(Object val) {
		if (val instanceof Integer) return (Integer) val;
		if (val instanceof Long)    return ((Long) val).intValue();
		if (val instanceof Number)  return ((Number) val).intValue();
		try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return 0; }
	}

	private Claims parseClaims(String token) {
		return Jwts.parserBuilder()
				.setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
				.build()
				.parseClaimsJws(token)
				.getBody();
	}
}
