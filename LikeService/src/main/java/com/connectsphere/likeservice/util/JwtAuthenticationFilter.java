package com.connectsphere.likeservice.util;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtUtil jwtUtil;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getRequestURI();

		if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/webjars")) {
			filterChain.doFilter(request, response);
			return;
		}

		// Strategy 1: validate JWT token (Bearer header or cookie)
		String token = extractToken(request);
		if (token != null && jwtUtil.validateToken(token)) {
			int userId = jwtUtil.extractUserId(token);
			if (userId > 0) {
				String role = jwtUtil.extractRole(token);
				String email = jwtUtil.extractEmail(token);
				setAuthentication(userId, role);
				log.debug("Authenticated via JWT: userId={}, email={}", userId, email);
				filterChain.doFilter(request, response);
				return;
			}
		}

		// Strategy 2: API-gateway-forwarded X-User-Id header (gateway already validated JWT)
		String xUserId = request.getHeader("X-User-Id");
		if (xUserId != null && !xUserId.isBlank()) {
			try {
				int userId = Integer.parseInt(xUserId.trim());
				if (userId > 0) {
					String role = request.getHeader("X-User-Role");
					setAuthentication(userId, role);
					log.debug("Authenticated via X-User-Id header: userId={}", userId);
					filterChain.doFilter(request, response);
					return;
				}
			} catch (NumberFormatException e) {
				log.warn("Invalid X-User-Id header value: {}", xUserId);
			}
		}

		// No authentication resolved — proceed unauthenticated (Spring Security will block protected endpoints)
		filterChain.doFilter(request, response);
	}

	private void setAuthentication(int userId, String role) {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
				userId, null,
				List.of(new SimpleGrantedAuthority(role != null ? role : "ROLE_USER")));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private String extractToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			return header.substring(7);
		}

		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if ("cs_access_token".equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}

		return null;
	}
}
