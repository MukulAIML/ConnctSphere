package com.connectsphere.commentservice.config;

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

import com.connectsphere.commentservice.util.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtFilter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) ->
								response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
				.authorizeHttpRequests(auth -> auth
						// ── Swagger / OpenAPI ──────────────────────────────────────────────
						.requestMatchers(
								"/swagger-ui/**", "/api/swagger-ui/**", "/api/swagger-ui.html",
								"/v3/api-docs/**", "/v3/api-docs", "/webjars/**",
								"/swagger-resources/**", "/configuration/**")
						.permitAll()

						// ── Admin moderation — ROLE_ADMIN / ROLE_MODERATOR ───────────────
						.requestMatchers("/admin/comments/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")

						// ── Public read endpoints ─────────────────────────────────────────
						.requestMatchers(HttpMethod.GET,
								"/comments", "/comments/post/**", "/comments/*/replies",
								"/comments/user/**", "/comments/*", "/comments/post/*/count")
						.permitAll()
						.requestMatchers(HttpMethod.PUT,
								"/comments/*/like/increment",
								"/comments/*/like/decrement")
						.permitAll()

						// ── Authenticated write endpoints ─────────────────────────────────
						.requestMatchers("/comments").authenticated()
						.requestMatchers("/comments/*/replies", "/comments/*", "/comments/*/unlike",
								"/comments/*/like")
						.authenticated()

						.anyRequest().authenticated())
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
