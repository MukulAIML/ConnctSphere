package com.connectsphere.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AuthServiceApplication — entry point for the ConnectSphere Auth Service.
 *
 * Microservice responsibilities:
 *   • User registration (LOCAL + OAuth2: Google / GitHub)
 *   • JWT issuance, validation, and refresh
 *   • Profile management and password changes
 *   • Account deactivation
 *   • Full-text user search
 *
 * Default port: 8080
 * Swagger UI:   http://localhost:8080/swagger-ui.html
 * Health:       http://localhost:8080/actuator/health
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
