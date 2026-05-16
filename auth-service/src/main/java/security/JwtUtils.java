package com.connectsphere.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JwtUtils — handles JWT creation, parsing, and validation.
 *
 * Tokens carry:  sub (userId), email, role
 * Expiry: configurable via app.jwt.expiration-ms (default 24 h)
 */
@Component
@Slf4j
public class JwtUtils {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // ── Generate ─────────────────────────────────────────

    public String generateToken(Long userId, String email, String role) {
        return generateAccessToken(userId, email, role);
    }

    public String generateAccessToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, ACCESS_TOKEN_TYPE, jwtExpirationMs);
    }

    public String generateRefreshToken(Long userId, String email, String role) {
        return buildToken(userId, email, role, REFRESH_TOKEN_TYPE, refreshExpirationMs);
    }

    private String buildToken(Long userId, String email, String role, String tokenType, long expirationMs) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Parse ────────────────────────────────────────────

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserIdFromToken(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    public String getTokenTypeFromToken(String token) {
        return parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
    }

    // ── Validate ─────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT empty/null: {}", e.getMessage());
        }
        return false;
    }

    // ── Refresh ──────────────────────────────────────────

    public String refreshToken(String token) {
        Claims claims = parseClaims(token);
        return generateAccessToken(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_ROLE, String.class)
        );
    }

    // ── TTL helper (for Redis blocklist) ─────────────────

    /**
     * Returns the number of seconds remaining until the token expires.
     * Returns 0 if the token is already expired or unparseable.
     */
    public long getRemainingTtlSeconds(String token) {
        try {
            Date expiry = parseClaims(token).getExpiration();
            long remaining = (expiry.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }
}
