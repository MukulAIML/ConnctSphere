package com.connectsphere.commentservice.config;

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
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI commentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectSphere — Comment Service API")
                        .description("""
                                REST API for the Comment Service of ConnectSphere.
                                
                                **Authentication**: Most write endpoints require a valid JWT supplied either as:
                                - `Authorization: Bearer <token>` header, or
                                - `cs_access_token` cookie.
                                
                                **Admin endpoints** (`/admin/comments/**`) additionally require the JWT to carry
                                `ROLE_ADMIN` as the role claim.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ConnectSphere Platform Team")
                                .email("platform@connectsphere.com")))
                .servers(List.of(
                        new Server().url("/").description("Current host")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter the JWT token obtained from the Auth Service.")));
    }
}
