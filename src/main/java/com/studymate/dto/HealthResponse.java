package com.studymate.dto;

import java.time.Instant;

/**
 * HealthResponse represents the diagnostic status of the StudyMate application.
 *
 * Concepts for Beginners:
 * - A Java 'record' (introduced in Java 14/16) is a concise way to create immutable data carriers.
 * - It automatically generates private final fields, a canonical constructor, getters (e.g. status()),
 *   equals(), hashCode(), and toString().
 * - Jackson (Spring's JSON serializer) automatically serializes this record into JSON for the API client.
 *
 * @param status    e.g., "UP" indicating the service is healthy
 * @param appName   The configured application name
 * @param timestamp The server-side UTC instant when the check was performed
 * @param stage     The current implementation stage completed
 */
public record HealthResponse(
        String status,
        String appName,
        Instant timestamp,
        String stage
) {
}
