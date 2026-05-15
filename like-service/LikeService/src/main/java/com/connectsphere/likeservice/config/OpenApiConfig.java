package com.connectsphere.likeservice.config;

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
    public OpenAPI likeServiceOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("ConnectSphere Like Service API")
                        .description("""
                                REST API for managing reactions (likes) on posts and comments in ConnectSphere.
                                
                                Supports Facebook-style reactions: LIKE, LOVE, HAHA, WOW, SAD, ANGRY.
                                A user can hold at most one reaction per target — reacting again with a different
                                type replaces the old reaction in-place without incrementing the total count.
                                
                                **Authentication:** Protected endpoints require a valid JWT bearer token issued
                                by the ConnectSphere Auth Service.
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
