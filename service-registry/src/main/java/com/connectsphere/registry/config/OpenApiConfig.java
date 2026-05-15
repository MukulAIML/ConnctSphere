package com.connectsphere.registry.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceRegistryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ConnectSphere Service Registry API")
                        .description("Eureka service discovery registry for ConnectSphere microservices.")
                        .version("1.0.0")
                        .contact(new Contact().name("ConnectSphere Team")));
    }
}
