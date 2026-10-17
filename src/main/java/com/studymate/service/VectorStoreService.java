package com.studymate.service;

import com.studymate.dto.SearchResultItem;
import com.studymate.exception.EmptyPdfException;
import com.studymate.exception.NoDocumentIndexedException;
import com.studymate.model.ExtractedDocument;
import com.studymate.model.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service responsible for converting TextChunks into Spring AI Documents,
 * managing in-memory vector indexing, preventing stale chunks, and executing similarity retrieval.
 *
 * Concepts for Beginners:
 * - Spring AI Document: The standardized abstraction that bundles text content with numerical
 *   vector embeddings and arbitrary key-value metadata (pageNumber, filename, documentId).
 * - Stale Chunk Prevention: When a user re-indexes or replaces an uploaded document, previous
 *   chunk IDs must be explicitly deleted from the VectorStore; otherwise, similarity search
 *   will return outdated or ghost passages from previous documents.
 * - Consistency Guarantee: If embedding or vector storage fails mid-operation, the internal state
 *   is not updated, preventing inconsistent or half-indexed documents.
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final VectorStore vectorStore;
    private final ChunkingService chunkingService;

    // State tracking for the currently indexed document
    private final AtomicReference<String> indexedDocumentId = new AtomicReference<>(null);
    private final Set<String> indexedChunkIds = ConcurrentHashMap.newKeySet();
    private final Object stateLock = new Object();

    public VectorStoreService(VectorStore vectorStore, ChunkingService chunkingService) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
    }

    /**
     * Converts the provided ExtractedDocument into chunks, builds Spring AI Documents with metadata,
     * and atomically stores them in the VectorStore while evicting any previously indexed document.
     *
     * @param document The extracted PDF document to index
     * @return Total number of chunks successfully indexed
     */
    public int indexDocument(ExtractedDocument document) {
        if (document == null || document.pages() == null || document.pages().isEmpty()) {
            throw new IllegalArgumentException("Cannot index an empty or null document.");
        }

        List<TextChunk> chunks = chunkingService.chunkDocument(document);
        if (chunks.isEmpty()) {
            throw new EmptyPdfException("Document contains no extractable text chunks to index.");
        }

        // Convert domain TextChunks to Spring AI Document entities with full citation metadata
        List<Document> springAiDocs = new ArrayList<>(chunks.size());
        List<String> newChunkIds = new ArrayList<>(chunks.size());

        for (TextChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", chunk.documentId());
            metadata.put("filename", chunk.sourceFilename());
            metadata.put("pageNumber", chunk.pageNumber());
            metadata.put("chunkIndex", chunk.chunkIndex());
            metadata.put("characterCount", chunk.characterCount());

            Document doc = new Document(chunk.chunkId(), chunk.text(), metadata);
            springAiDocs.add(doc);
            newChunkIds.add(chunk.chunkId());
        }

        synchronized (stateLock) {
            List<String> oldChunkIds = new ArrayList<>(indexedChunkIds);

            log.info("Indexing {} chunks for documentId: '{}', file: '{}' into VectorStore.",
                    springAiDocs.size(), document.documentId(), document.filename());

            // 1. Add new documents to the vector store
            // If this throws an exception (e.g. embedding failure), state remains untouched
            vectorStore.add(springAiDocs);

            // 2. Remove previous document's chunk IDs to prevent stale vectors
            if (!oldChunkIds.isEmpty()) {
                try {
                    vectorStore.delete(oldChunkIds);
                    log.info("Evicted {} previous chunks from VectorStore.", oldChunkIds.size());
                } catch (Exception e) {
                    log.warn("Warning during stale chunk eviction: {}", e.getMessage());
                }
            }

            // 3. Atomically update consistent state tracking
            indexedChunkIds.clear();
            indexedChunkIds.addAll(newChunkIds);
            indexedDocumentId.set(document.documentId());
        }

        log.info("Successfully completed vector indexing for documentId: '{}' ({} chunks).",
                document.documentId(), springAiDocs.size());

        return springAiDocs.size();
    }

    /**
     * Executes cosine similarity search against indexed vector chunks.
     *
     * @param query        The search query
     * @param topK         Maximum number of matches to retrieve
     * @param minThreshold Minimum similarity score filter (0.0 to 1.0)
     * @return List of SearchResultItem records sorted by descending similarity score
     */
    public List<SearchResultItem> similaritySearch(String query, int topK, double minThreshold) {
        String currentDocId = indexedDocumentId.get();
        if (currentDocId == null || indexedChunkIds.isEmpty()) {
            throw new NoDocumentIndexedException(
                    "No document has been indexed yet. Please upload and index a document first via POST /api/documents/index.");
        }

        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query must not be blank.");
        }

        SearchRequest request = SearchRequest.builder()
                .query(query.trim())
                .topK(Math.max(1, topK))
                .similarityThreshold(Math.max(0.0, minThreshold))
                .build();

        List<Document> matchedDocs = vectorStore.similaritySearch(request);
        if (matchedDocs == null || matchedDocs.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchResultItem> results = new ArrayList<>(matchedDocs.size());
        for (Document doc : matchedDocs) {
            Map<String, Object> metadata = doc.getMetadata();

            String chunkDocId = metadata.containsKey("documentId")
                    ? String.valueOf(metadata.get("documentId"))
                    : currentDocId;

            int pageNumber = metadata.containsKey("pageNumber") && metadata.get("pageNumber") instanceof Number num
                    ? num.intValue()
                    : 1;

            int chunkIndex = metadata.containsKey("chunkIndex") && metadata.get("chunkIndex") instanceof Number num
                    ? num.intValue()
                    : 0;

            String filename = metadata.containsKey("filename")
                    ? String.valueOf(metadata.get("filename"))
                    : "unknown.pdf";

            double score = doc.getScore() != null ? doc.getScore() : 0.0;

            results.add(new SearchResultItem(
                    doc.getId(),
                    chunkDocId,
                    doc.getText(),
                    pageNumber,
                    chunkIndex,
                    filename,
                    score
            ));
        }

        return results;
    }

    /**
     * Clears all indexed vectors and resets tracking state.
     */
    public void clearStore() {
        synchronized (stateLock) {
            if (!indexedChunkIds.isEmpty()) {
                try {
                    vectorStore.delete(new ArrayList<>(indexedChunkIds));
                } catch (Exception e) {
                    log.warn("Error clearing vector store: {}", e.getMessage());
                }
                indexedChunkIds.clear();
            }
            indexedDocumentId.set(null);
        }
    }

    public boolean hasIndexedDocument() {
        return indexedDocumentId.get() != null && !indexedChunkIds.isEmpty();
    }

    public String getIndexedDocumentId() {
        return indexedDocumentId.get();
    }

    public int getIndexedChunkCount() {
        return indexedChunkIds.size();
    }
}
