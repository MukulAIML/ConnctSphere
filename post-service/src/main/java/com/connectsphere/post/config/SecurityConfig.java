package com.connectsphere.post.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // Disable CSRF — REST API, stateless
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless — no HTTP sessions; auth comes from JWT on every request
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── IMPORTANT: return 401 (not 403) when no credentials are supplied ──
            // Spring Security's default AccessDeniedHandler returns 403 for both
            // "unauthenticated" and "authenticated but no permission" cases.
            // The exceptionHandling block below separates those two cases:
            //   • AuthenticationEntryPoint → 401  (no token / invalid token)
            //   • AccessDeniedHandler      → 403  (valid token but wrong role)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                })
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth

                // Public / infrastructure endpoints
                .requestMatchers(
                    "/posts/test",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/v3/api-docs/**",
                    "/webjars/**"
                ).permitAll()

                // Public read endpoints
                .requestMatchers(
                        HttpMethod.GET,
                        "/posts",
                        "/posts/*",
                        "/posts/user/**",
                        "/posts/search"
                ).permitAll()

                // Internal service-to-service engagement counters (no JWT needed)
                .requestMatchers(
                        HttpMethod.PUT,
                        "/posts/*/like/increment",
                        "/posts/*/like/decrement",
                        "/posts/*/comment/increment",
                        "/posts/*/comment/decrement",
                        "/posts/*/mediaUrls"
                ).permitAll()

                // Reporting + moderation endpoints
                .requestMatchers(HttpMethod.POST, "/posts/reports").authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/reports/mine").authenticated()

                // Dashboard endpoints (admin only)
                .requestMatchers("/posts/admin/dashboard/**").hasAuthority("ROLE_ADMIN")

                // Report queue moderation (admin + moderator)
                .requestMatchers("/posts/admin/reports/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")

                // Post moderation (admin + moderator); kept on both paths for backward compatibility
                .requestMatchers("/admin/posts/**", "/posts/admin/posts/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")

                // All other /posts/** require a valid JWT
                .requestMatchers("/posts/**").authenticated()

                // Catch-all
                .anyRequest().authenticated()
            )

            // JWT filter runs BEFORE Spring Security's own auth filter
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
