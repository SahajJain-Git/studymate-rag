package com.studymate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration for Cross-Origin Resource Sharing (CORS).
 *
 * Concepts for Beginners:
 * - What is CORS?
 *   When a web browser loads a frontend from one origin (e.g. http://localhost:3000)
 *   and makes HTTP fetch() requests to a backend on another port (e.g. http://localhost:8080),
 *   the browser blocks the request by default for security unless the backend sends
 *   Access-Control-Allow-Origin headers.
 * - Local Development Policy:
 *   We allow local origins matching http://localhost:* and http://127.0.0.1:* across standard
 *   HTTP methods (GET, POST, OPTIONS) so static frontend servers can communicate with the backend.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
