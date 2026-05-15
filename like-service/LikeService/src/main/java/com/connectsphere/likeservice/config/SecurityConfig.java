package com.connectsphere.likeservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.connectsphere.likeservice.util.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtFilter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/likes", "/api/likes")
						.authenticated()
						.requestMatchers(HttpMethod.DELETE, "/likes", "/api/likes")
						.authenticated()
						.requestMatchers(HttpMethod.PUT, "/likes", "/api/likes")
						.authenticated()
						.requestMatchers("/likes/hasLiked", "/api/likes/hasLiked", "/likes/target/*/*/me",
								"/api/likes/target/*/*/me", "/likes/target/*/*/has-liked",
								"/api/likes/target/*/*/has-liked")
						.authenticated()
						.requestMatchers("/likes/count", "/api/likes/count", "/likes/target/*/*",
								"/api/likes/target/*/*", "/likes/target/*/*/count", "/api/likes/target/*/*/count",
								"/likes/target/*/*/count-by-type", "/api/likes/target/*/*/count-by-type",
								"/likes/target/*/*/summary", "/api/likes/target/*/*/summary", "/likes/user/*",
								"/api/likes/user/*")
						.permitAll()
						.requestMatchers("/swagger-ui/**", "/api/swagger-ui/**", "/api/swagger-ui.html", "/v3/api-docs/**",
								"/v3/api-docs", "/webjars/**", "/swagger-resources/**", "/configuration/**")
						.permitAll()
						.anyRequest().permitAll())
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
	}
}
