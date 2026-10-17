package com.studymate.dto;

/**
 * Response DTO returned after indexing a document into the vector store.
 *
 * @param documentId         Unique upload ID of the indexed document
 * @param filename           Original PDF filename
 * @param totalChunksIndexed Number of chunks converted and added to the vector store
 * @param message            Human-readable status confirmation
 */
public record IndexResponse(
        String documentId,
        String filename,
        int totalChunksIndexed,
        String message
) {
}
