package com.studymate.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for semantic similarity search against indexed vector chunks.
 *
 * @param query        The natural language query or question
 * @param topK         Maximum number of most-similar chunks to return (defaults to 4)
 * @param minThreshold Minimum cosine similarity score threshold (0.0 to 1.0, defaults to 0.0)
 */
public record SearchQueryRequest(
        @NotBlank(message = "Search query must not be blank.")
        String query,
        Integer topK,
        Double minThreshold
) {
    public SearchQueryRequest {
        if (topK == null || topK <= 0) {
            topK = 4;
        }
        if (minThreshold == null || minThreshold < 0.0) {
            minThreshold = 0.0;
        }
    }

    public SearchQueryRequest(String query) {
        this(query, 4, 0.0);
    }
}
