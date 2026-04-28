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

                // Public endpoints
                .requestMatchers(
                    "/posts/test",          // health / smoke test
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/posts",
                        "/posts/*",
                        "/posts/feed/**",
                        "/posts/user/**",
                        "/posts/search"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.PUT,
                        "/posts/*/like/increment",
                        "/posts/*/like/decrement",
                        "/posts/*/comment/increment",
                        "/posts/*/comment/decrement",
                        "/posts/*/mediaUrls"
                ).permitAll()

                // All /posts/** require a valid JWT
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
