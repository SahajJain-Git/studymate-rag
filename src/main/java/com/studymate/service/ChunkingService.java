package com.studymate.service;

import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import com.studymate.model.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for partitioning document text into overlapping semantic windows.
 *
 * Concepts for Beginners:
 * - Why Chunk?
 *   LLMs have fixed context limits, and vector databases perform semantic search by comparing
 *   embeddings of focused passages. Feeding an entire 50-page document at once causes retrieval
 *   dilution and hallucinations.
 * - Why Overlap?
 *   If a sentence or key concept falls directly on the boundary between chunk N and chunk N+1,
 *   a sliding overlap (e.g. 50 characters) ensures that boundary concepts appear intact in at least
 *   one of the chunks, preventing context fragmentation.
 * - Page-Level Preservation:
 *   Chunking is performed per-page. Chunks do not bleed across page boundaries so that every chunk
 *   can be attributed to an exact physical page number for citations.
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private final int chunkSize;
    private final int overlap;

    public ChunkingService(
            @Value("${studymate.chunking.chunk-size:500}") int chunkSize,
            @Value("${studymate.chunking.overlap:50}") int overlap) {

        validateConfiguration(chunkSize, overlap);
        this.chunkSize = chunkSize;
        this.overlap = overlap;

        log.info("Initialized ChunkingService with chunkSize={} characters, overlap={} characters",
                this.chunkSize, this.overlap);
    }

    /**
     * Splits an ExtractedDocument into an ordered list of TextChunks.
     *
     * @param document The extracted document containing pages
     * @return List of TextChunks with source metadata and page numbers preserved
     */
    public List<TextChunk> chunkDocument(ExtractedDocument document) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) {
            return List.of();
        }

        List<TextChunk> allChunks = new ArrayList<>();
        AtomicInteger globalChunkIndex = new AtomicInteger(0);

        for (ExtractedPage page : document.pages()) {
            List<TextChunk> pageChunks = chunkText(
                    page.text(),
                    page.pageNumber(),
                    document.filename(),
                    document.documentId(),
                    this.chunkSize,
                    this.overlap,
                    globalChunkIndex
            );
            allChunks.addAll(pageChunks);
        }

        log.info("Chunked document '{}' [id: {}] ({} pages) into {} total chunks.",
                document.filename(), document.documentId(), document.totalPages(), allChunks.size());

        return allChunks;
    }

    /**
     * Splits a specific text string using the sliding window algorithm.
     *
     * @param text             The raw text to chunk
     * @param pageNumber       1-based page number for citation
     * @param sourceFilename   Name of the original PDF
     * @param documentId       Unique document upload ID
     * @param customChunkSize  Window size in characters
     * @param customOverlap    Sliding overlap in characters
     * @param chunkIndexCounter Thread-safe sequential index counter
     * @return List of generated TextChunks
     */
    public List<TextChunk> chunkText(
            String text,
            int pageNumber,
            String sourceFilename,
            String documentId,
            int customChunkSize,
            int customOverlap,
            AtomicInteger chunkIndexCounter) {

        validateConfiguration(customChunkSize, customOverlap);

        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        String cleanText = text.trim();
        int textLength = cleanText.length();
        List<TextChunk> chunks = new ArrayList<>();

        // If text is shorter than or equal to chunk size, produce exactly one chunk
        if (textLength <= customChunkSize) {
            int index = chunkIndexCounter.getAndIncrement();
            String chunkId = generateChunkId(documentId, sourceFilename, pageNumber, index);
            chunks.add(new TextChunk(chunkId, documentId, cleanText, pageNumber, index, sourceFilename));
            return chunks;
        }

        // Sliding window: advance by (chunkSize - overlap) characters on each iteration
        int step = customChunkSize - customOverlap;
        int start = 0;

        while (start < textLength) {
            int end = Math.min(start + customChunkSize, textLength);
            String chunkContent = cleanText.substring(start, end).trim();

            if (!chunkContent.isEmpty()) {
                int index = chunkIndexCounter.getAndIncrement();
                String chunkId = generateChunkId(documentId, sourceFilename, pageNumber, index);
                chunks.add(new TextChunk(chunkId, documentId, chunkContent, pageNumber, index, sourceFilename));
            }

            // If this chunk reached the end of the text, stop
            if (end >= textLength) {
                break;
            }

            start += step;
        }

        return chunks;
    }

    /**
     * Backward-compatible overload for callers without explicit documentId.
     */
    public List<TextChunk> chunkText(
            String text,
            int pageNumber,
            String sourceFilename,
            int customChunkSize,
            int customOverlap,
            AtomicInteger chunkIndexCounter) {
        return chunkText(text, pageNumber, sourceFilename, "doc-default", customChunkSize, customOverlap, chunkIndexCounter);
    }

    /**
     * Validates that chunkSize and overlap parameters conform to required mathematical bounds.
     */
    public static void validateConfiguration(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    String.format("chunk-size must be greater than 0, got: %d", chunkSize));
        }
        if (overlap < 0) {
            throw new IllegalArgumentException(
                    String.format("overlap must be non-negative, got: %d", overlap));
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    String.format("overlap (%d) must be strictly less than chunk-size (%d).", overlap, chunkSize));
        }
    }

    private String generateChunkId(String documentId, String filename, int pageNumber, int index) {
        String safeDocId = (documentId == null || documentId.isBlank()) ? "doc" : documentId;
        String safeName = (filename == null) ? "doc" : filename.replaceAll("[^a-zA-Z0-9.-]", "_");
        return String.format("%s-%s-p%d-c%d", safeDocId, safeName, pageNumber, index);
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }
}
