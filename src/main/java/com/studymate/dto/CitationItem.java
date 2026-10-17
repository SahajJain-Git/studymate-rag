package com.studymate.dto;

/**
 * Represents a verifiable citation constructed directly from retrieved chunk metadata.
 *
 * Concepts for Beginners:
 * - Deterministic Citations: We NEVER ask the LLM to invent or guess page numbers or chunk IDs.
 *   Instead, citations are constructed authoritatively in Java from the actual vector search
 *   chunks returned by VectorStoreService.
 * - Traceability: If an AI makes a claim, the student can immediately look up the exact source
 *   page number and read the raw text snippet to verify the answer.
 *
 * @param pageNumber     1-based physical page number from the source PDF
 * @param sourceFilename Original PDF filename containing this passage
 * @param chunkId        Unique ID of the matched chunk
 * @param score          Cosine similarity score (0.0 to 1.0)
 * @param textPreview    Excerpt of the retrieved passage
 */
public record CitationItem(
        int pageNumber,
        String sourceFilename,
        String chunkId,
        double score,
        String textPreview
) {
}
