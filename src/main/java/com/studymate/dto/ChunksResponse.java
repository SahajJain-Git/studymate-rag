package com.studymate.dto;

import com.studymate.model.TextChunk;

import java.util.List;

/**
 * Response DTO returned by GET /api/documents/chunks to inspect the active document's chunks.
 *
 * @param filename    Source document filename
 * @param totalPages  Total number of pages in the source document
 * @param totalChunks Total number of chunks generated
 * @param chunkSize   The configured window size in characters
 * @param overlap     The configured sliding overlap in characters
 * @param chunks      The list of generated TextChunk records with metadata
 * @param message     Human-readable status summary
 */
public record ChunksResponse(
        String filename,
        int totalPages,
        int totalChunks,
        int chunkSize,
        int overlap,
        List<TextChunk> chunks,
        String message
) {
}
