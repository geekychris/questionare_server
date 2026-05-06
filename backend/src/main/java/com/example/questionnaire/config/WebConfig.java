package com.example.questionnaire.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // The previous "http://localhost:3000" allow-list rejected calls
        // from the deployed frontend (k8s NodePort 30006, docker-compose
        // 18008, etc.). Use allowedOriginPatterns so any origin is OK; the
        // pattern form is required when allowCredentials is true.
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

