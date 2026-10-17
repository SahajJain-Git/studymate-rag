package com.studymate.dto;

/**
 * Represents an individual retrieved chunk with its similarity score and citation metadata.
 *
 * @param chunkId        Unique ID of the matched chunk
 * @param documentId     Parent document ID
 * @param text           Text snippet of the matched chunk
 * @param pageNumber     1-based source page number for citations
 * @param chunkIndex     Sequential index of the chunk
 * @param sourceFilename Original source filename
 * @param score          Cosine similarity score (0.0 to 1.0)
 */
public record SearchResultItem(
        String chunkId,
        String documentId,
        String text,
        int pageNumber,
        int chunkIndex,
        String sourceFilename,
        double score
) {
}
