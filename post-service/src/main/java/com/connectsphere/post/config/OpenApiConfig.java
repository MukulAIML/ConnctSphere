package com.connectsphere.post.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 (Swagger) documentation configuration.
 * Swagger UI available at: /swagger-ui.html
 * OpenAPI JSON at:         /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI postServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectSphere – Post Service API")
                        .description("""
                                REST API for the Post micro-service of ConnectSphere.
                                
                                Provides endpoints for:
                                - Creating, editing, and deleting posts
                                - Personalized news-feed (Redis-cached, < 1.5 s SLA)
                                - Engagement counters (likes / comments)
                                - Content moderation (AWS Rekognition)
                                - Admin moderation hooks
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ConnectSphere Engineering")
                                .email("engineering@connectsphere.io"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://connectsphere.io")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local development"),
                        new Server().url("http://post-service:8081").description("Docker / K8s internal")))
                // Bearer JWT authentication
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth",
                                new SecurityScheme()
                                        .name("BearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token (without the 'Bearer ' prefix)")));
    }
}
