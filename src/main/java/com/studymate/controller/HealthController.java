package com.studymate.controller;

import com.studymate.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * HealthController provides diagnostic endpoints to verify application liveness.
 *
 * Concepts for Beginners:
 * - @RestController: Combines @Controller and @ResponseBody. Tells Spring that return values
 *   should be serialized directly into the HTTP response body as JSON.
 * - @RequestMapping("/api"): Sets the base URI path prefix for all endpoints in this controller.
 * - @Value("${...}"): Injects property values from application.yml into Spring beans.
 * - ResponseEntity<T>: A Spring wrapper representing the entire HTTP response (status code, headers, and body).
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name:studymate}") String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Diagnostic endpoint to confirm the server is running and ready.
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> checkHealth() {
        HealthResponse response = new HealthResponse(
                "UP",
                applicationName,
                Instant.now(),
                "Stage 1 - Spring Boot Foundation"
        );
        return ResponseEntity.ok(response);
    }
}
