package com.studymate.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for RAG question answering.
 *
 * Concepts for Beginners:
 * - @NotBlank: A Jakarta Validation annotation that ensures the client supplies a non-null,
 *   non-empty, non-whitespace question string before the controller even touches business logic.
 * - Compact Constructor: Automatically sets sensible default values for optional parameters
 *   like topK and minThreshold if the client omits them.
 *
 * @param question     The natural-language question asked by the student
 * @param topK         Maximum number of relevant context chunks to retrieve (defaults to 4)
 * @param minThreshold Minimum similarity score filter between 0.0 and 1.0 (defaults to 0.0)
 */
public record QuestionRequest(
        @NotBlank(message = "Question must not be blank.")
        String question,
        Integer topK,
        Double minThreshold
) {
    public QuestionRequest {
        if (topK == null || topK <= 0) {
            topK = 4;
        }
        if (minThreshold == null || minThreshold < 0.0) {
            minThreshold = 0.0;
        }
    }

    public QuestionRequest(String question) {
        this(question, 4, 0.0);
    }
}
