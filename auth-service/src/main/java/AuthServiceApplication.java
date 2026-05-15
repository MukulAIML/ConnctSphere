package com.connectsphere.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// Main entry point for Auth Service
// Bootstraps Spring Boot application and starts embedded server

@SpringBootApplication // Enables auto-configuration, component scanning, and configuration support
@EnableDiscoveryClient // Registers this service with Eureka (Service Discovery)
public class AuthServiceApplication {

    public static void main(String[] args) {
        // Launches the Spring Boot application
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
