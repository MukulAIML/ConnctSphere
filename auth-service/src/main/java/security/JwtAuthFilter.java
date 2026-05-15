package com.connectsphere.auth.security;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.service.RedisSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthFilter — intercepts every request and populates the
 * SecurityContext if a valid, non-blocklisted Bearer token is present.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final RedisSessionService redisSessionService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            // Reject tokens that were explicitly invalidated via logout
            if (redisSessionService.isTokenBlocked(token)) {
                log.warn("Rejected blocklisted token");
                filterChain.doFilter(request, response);
                return;
            }
            try {
                Long userId = jwtUtils.getUserIdFromToken(token);
                User user = userRepository.findByUserId(userId).orElse(null);
                if (user == null) {
                    log.warn("Rejected token for unknown userId={}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getIsSuspended())) {
                    log.warn("Rejected token for inactive/suspended userId={}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                String role = StringUtils.hasText(user.getRole())
                        ? user.getRole()
                        : (StringUtils.hasText(jwtUtils.getRoleFromToken(token))
                                ? jwtUtils.getRoleFromToken(token)
                                : "ROLE_USER");

                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                log.warn("Could not set user authentication from token: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
