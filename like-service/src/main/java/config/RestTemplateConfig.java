package com.connectsphere.like.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
public class RestTemplateConfig {

    private static final Logger logger = LoggerFactory.getLogger(RestTemplateConfig.class);

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new RestTemplateInterceptor());
        return restTemplate;
    }

    private static class RestTemplateInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            logger.info("Outbound Request: [{}] {}", request.getMethod(), request.getURI());
            
            // Propagate JWT Token if present in Security Context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() instanceof String) {
                request.getHeaders().setBearerAuth((String) auth.getCredentials());
            }

            ClientHttpResponse response;
            try {
                response = execution.execute(request, body);
                logger.info("Outbound Response: {} from {}", response.getStatusCode(), request.getURI());
                return response;
            } catch (IOException e) {
                logger.error("Outbound Request Failed: [{}] {} - Error: {}", request.getMethod(), request.getURI(), e.getMessage());
                throw e;
            }
        }
    }
}
