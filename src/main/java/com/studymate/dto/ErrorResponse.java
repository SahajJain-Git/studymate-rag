package com.studymate.dto;

import java.time.Instant;

/**
 * Standardized error payload returned across all API endpoints when an exception occurs.
 *
 * Concepts for Beginners:
 * - Consistent Error Structure: Frontend clients can always expect the same JSON keys
 *   (timestamp, status, error, message, path) whenever any HTTP 4xx or 5xx occurs.
 * - Security: Stack traces and internal server paths are never leaked to clients.
 *
 * @param timestamp Server UTC timestamp of the error
 * @param status    HTTP status code (e.g. 400, 413, 422, 500)
 * @param error     HTTP status reason phrase (e.g. "Bad Request", "Unprocessable Entity")
 * @param message   User-friendly explanation of what went wrong
 * @param path      The requested URI path that triggered the error
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
