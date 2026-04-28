package com.connectsphere.comment.config;

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
            //Disable CSRF for REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            //No sessions (microservices are stateless)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            //Authorization rules
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.GET, "/comments/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/comments/*/like").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/comments/*/like").permitAll()


                // Protected endpoints (JWT required)
                .requestMatchers("/comments/**").authenticated()

                // Any other request must be authenticated
                .anyRequest().authenticated()
            )

            //Add JWT filter before Spring Security auth filter
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
