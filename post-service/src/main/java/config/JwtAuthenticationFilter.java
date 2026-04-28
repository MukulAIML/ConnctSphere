package com.connectsphere.post.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && validateToken(jwt)) {
                Claims claims = getClaimsFromToken(jwt);

                String userId = claims.getSubject();
                String role   = claims.get("role", String.class);

                if (userId != null) {
                    // ── CRITICAL FIX ─────────────────────────────────────────────────────────
                    // If 'role' claim is null, new SimpleGrantedAuthority(null) throws an
                    // IllegalArgumentException that is swallowed by the outer catch block,
                    // leaving SecurityContextHolder empty → Spring Security sees no auth → 403.
                    // Default to ROLE_USER when the claim is absent.
                    // ─────────────────────────────────────────────────────────────────────────
                    String authority = (role != null && !role.isBlank()) ? role : "ROLE_USER";

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId,
                                    jwt,
                                    List.of(new SimpleGrantedAuthority(authority))
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("JWT OK – userId=" + userId + " authority=" + authority
                            + " uri=" + request.getRequestURI());
                }
            } else {
                // Log when a token is present but fails validation so we can see it in logs
                String authHeader = request.getHeader("Authorization");
                if (StringUtils.hasText(authHeader)) {
                    logger.warn("JWT validation failed for URI=" + request.getRequestURI()
                            + " | header starts with: "
                            + authHeader.substring(0, Math.min(authHeader.length(), 30)));
                }
            }

        } catch (Exception ex) {
            logger.error("JWT processing failed for URI=" + request.getRequestURI()
                    + " | " + ex.getMessage(), ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            logger.error("Invalid JWT: " + ex.getMessage());
        }
        return false;
    }

    private Claims getClaimsFromToken(String token) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}