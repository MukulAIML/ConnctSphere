package com.connectsphere.auth.config;

import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtAuthFilter;
import com.connectsphere.auth.security.JwtUtils;
import com.connectsphere.auth.security.UserDetailsServiceImpl;
import com.connectsphere.auth.service.RedisSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtUtils jwtUtils;
    private final RedisSessionService redisSessionService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtils, redisSessionService, userRepository);
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"status\": 401, \"message\": \"Unauthorized: Please provide a valid Bearer token\"}");
                })
            )
            .anonymous(anonymous -> anonymous.authorities("ROLE_GUEST"))
            .authorizeHttpRequests(auth -> auth
                // ✅ Public APIs
                .requestMatchers(
                    "/auth/register",
                    "/auth/login",
                    "/auth/refresh",
                    "/auth/validate",
                    "/auth/oauth2/**",
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**",
                    "/actuator/**"
                ).permitAll()

                // ✅ Admin APIs
                .requestMatchers("/auth/admin/**").hasRole("ADMIN")

                // ✅ Authenticated user APIs
                .requestMatchers(
                    HttpMethod.POST,
                    "/auth/logout"
                ).hasAnyRole("USER", "ADMIN")
                .requestMatchers(
                    "/auth/profile",
                    "/auth/password",
                    "/auth/deactivate",
                    "/auth/search",
                    "/auth/users/**"
                ).hasAnyRole("USER", "ADMIN")

                // ✅ Everything else needs JWT
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .successHandler(oAuth2LoginSuccessHandler)
            )

            // ✅ Use JWT instead of default login
            .authenticationProvider(authProvider())

            // ✅ Add JWT filter
            .addFilterBefore(jwtAuthFilter(),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
