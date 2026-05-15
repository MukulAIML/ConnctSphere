package com.connectsphere.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${media.local.storage-path:/tmp/connectsphere-media}")
    private String localStoragePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String normalizedPath = localStoragePath.endsWith("/")
                ? localStoragePath
                : localStoragePath + "/";

        registry.addResourceHandler("/media/files/**", "/api/v1/media/files/**")
                .addResourceLocations("file:" + normalizedPath);
    }
}
