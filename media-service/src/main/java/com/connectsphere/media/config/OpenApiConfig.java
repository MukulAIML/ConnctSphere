package com.connectsphere.media.config;

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

/**
 * OpenAPI 3.0 / Swagger configuration for the ConnectSphere Media Service.
 *
 * <p>Swagger UI is accessible at: {@code /swagger-ui.html}
 * <br>Raw OpenAPI JSON/YAML at:  {@code /api-docs}
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI mediaServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectSphere – Media Service API")
                        .description("""
                                REST API for media (images & videos) and stories management.
                                
                                **Storage**: Files are uploaded to AWS S3 and served via CloudFront CDN.
                                
                                **Authentication**: All endpoints (except Swagger UI and the internal
                                `/media/soft-delete` endpoint) require a JWT Bearer token issued by the
                                ConnectSphere Auth Service.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ConnectSphere Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8086").description("Local development"),
                        new Server().url("http://media-service:8086").description("Docker / K8s internal")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token (without the 'Bearer ' prefix)")));
    }
}
