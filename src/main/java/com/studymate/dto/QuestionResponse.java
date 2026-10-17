package com.studymate.dto;

import java.util.List;

/**
 * Response payload returned by POST /api/query containing the RAG-generated answer and citations.
 *
 * Concepts for Beginners:
 * - Groundedness: The answer is synthesized strictly from the retrieved chunks of the indexed document.
 * - hasSufficientContext: If no relevant passages were found in the document, this is set to false,
 *   and the answer clearly explains that the document does not contain relevant information.
 *
 * @param question             The original student question
 * @param answer               Context-grounded answer or insufficient context notice
 * @param citations            Authoritative list of source citations from retrieved chunks
 * @param documentId           Unique ID of the indexed document searched
 * @param hasSufficientContext True if relevant passages were retrieved to substantiate an answer
 */
public record QuestionResponse(
        String question,
        String answer,
        List<CitationItem> citations,
        String documentId,
        boolean hasSufficientContext
) {
    public QuestionResponse {
        citations = (citations == null) ? List.of() : List.copyOf(citations);
    }
}
