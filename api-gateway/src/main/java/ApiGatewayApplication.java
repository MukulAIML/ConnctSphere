package com.connectsphere.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import java.util.List;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public WebFilter corsDeduplicationFilter() {
        return (exchange, chain) -> chain.filter(exchange).then(Mono.fromRunnable(() -> {
            var responseHeaders = exchange.getResponse().getHeaders();
            List<String> origins = responseHeaders.get("Access-Control-Allow-Origin");
            if (origins != null && origins.size() > 1) {
                String uniqueOrigin = origins.get(origins.size() - 1);
                responseHeaders.set("Access-Control-Allow-Origin", uniqueOrigin);
            }
            List<String> credentials = responseHeaders.get("Access-Control-Allow-Credentials");
            if (credentials != null && credentials.size() > 1) {
                String uniqueCred = credentials.get(credentials.size() - 1);
                responseHeaders.set("Access-Control-Allow-Credentials", uniqueCred);
            }
        }));
    }
}
