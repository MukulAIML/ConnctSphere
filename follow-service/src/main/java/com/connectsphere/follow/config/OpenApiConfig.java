package com.connectsphere.follow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI followServiceOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("ConnectSphere Follow Service API")
                        .description("""
                                REST API for managing follow relationships between users in ConnectSphere.

                                Supports follow/unfollow, follower/following lists, mutual-follow detection,
                                follow-status checks, and people-you-may-know suggestions.

                                **Authentication:** Most endpoints require a valid JWT bearer token issued
                                by the ConnectSphere Auth Service. The follower/following list GET endpoints
                                are publicly accessible.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ConnectSphere Team")))
                .servers(List.of(
                        new Server().url("/").description("Default")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token obtained from the Auth Service")));
    }
}
