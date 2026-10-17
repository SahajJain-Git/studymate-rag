package com.studymate.controller;

import com.studymate.dto.ChunksResponse;
import com.studymate.dto.DocumentUploadResponse;
import com.studymate.dto.PageSummary;
import com.studymate.exception.NoDocumentUploadedException;
import com.studymate.model.ExtractedDocument;
import com.studymate.model.TextChunk;
import com.studymate.service.ChunkingService;
import com.studymate.service.InMemoryDocumentStore;
import com.studymate.service.PdfService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import com.studymate.dto.IndexResponse;
import com.studymate.dto.SearchQueryRequest;
import com.studymate.dto.SearchResponse;
import com.studymate.dto.SearchResultItem;
import com.studymate.service.VectorStoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * REST Controller providing endpoints for document upload, ingestion, chunk inspection, and vector search.
 *
 * Concepts for Beginners:
 * - Thin Controller Pattern: This class contains NO parsing, extraction, or chunking algorithms.
 *   It simply receives the HTTP requests, delegates to services/stores, and maps domain entities
 *   into clean API response DTOs.
 * - @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE): Handles multipart PDF uploads.
 * - @GetMapping("/chunks"): Allows inspecting the chunks produced by ChunkingService for the
 *   currently active document stored in InMemoryDocumentStore.
 * - @PostMapping("/index"): Converts chunks into Spring AI Documents and indexes them into VectorStore.
 * - @PostMapping("/search"): Performs top-k semantic similarity search against indexed vector chunks.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final PdfService pdfService;
    private final InMemoryDocumentStore documentStore;
    private final ChunkingService chunkingService;
    private final VectorStoreService vectorStoreService;

    public DocumentController(
            PdfService pdfService,
            InMemoryDocumentStore documentStore,
            ChunkingService chunkingService,
            VectorStoreService vectorStoreService) {
        this.pdfService = pdfService;
        this.documentStore = documentStore;
        this.chunkingService = chunkingService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Uploads, extracts text, and stores a PDF study document in memory.
     * POST /api/documents/upload
     *
     * @param file The multipart PDF file to process
     * @return DocumentUploadResponse containing page count, character count, and page summaries
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        // 1. Delegate text extraction to PdfService
        ExtractedDocument extracted = pdfService.extractText(file);

        // 2. Persist the complete extracted document in InMemoryDocumentStore across HTTP requests
        documentStore.save(extracted);

        // 3. Map domain entity to API response DTO (with friendly page previews)
        List<PageSummary> pageSummaries = extracted.pages().stream()
                .map(page -> {
                    String fullText = page.text();
                    String preview = fullText.length() > 120
                            ? fullText.substring(0, 120).trim() + "..."
                            : fullText;
                    return new PageSummary(page.pageNumber(), page.characterCount(), preview);
                })
                .toList();

        DocumentUploadResponse response = new DocumentUploadResponse(
                extracted.filename(),
                extracted.totalPages(),
                extracted.totalCharacters(),
                pageSummaries,
                String.format("Successfully parsed and stored %d pages (%d characters). Ready for chunking.",
                        extracted.totalPages(), extracted.totalCharacters())
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Inspects the text chunks generated from the currently active stored document.
     * GET /api/documents/chunks
     *
     * @return ChunksResponse containing all generated TextChunks and configuration metrics
     * @throws NoDocumentUploadedException if no document is currently held in memory (HTTP 404)
     */
    @GetMapping("/chunks")
    public ResponseEntity<ChunksResponse> getDocumentChunks() {
        // Retrieve the currently active document, or throw 404 if none has been uploaded
        ExtractedDocument document = documentStore.get()
                .orElseThrow(() -> new NoDocumentUploadedException(
                        "No document has been uploaded yet. Please upload a PDF first via POST /api/documents/upload"));

        // Partition the complete document text into chunks
        List<TextChunk> chunks = chunkingService.chunkDocument(document);

        ChunksResponse response = new ChunksResponse(
                document.filename(),
                document.totalPages(),
                chunks.size(),
                chunkingService.getChunkSize(),
                chunkingService.getOverlap(),
                chunks,
                String.format("Generated %d chunks from %d pages with chunk-size=%d and overlap=%d.",
                        chunks.size(), document.totalPages(), chunkingService.getChunkSize(), chunkingService.getOverlap())
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Indexes the currently active uploaded document into the VectorStore.
     * POST /api/documents/index
     *
     * @return IndexResponse confirming number of chunks indexed and document ID
     * @throws NoDocumentUploadedException if no document has been uploaded yet (HTTP 404)
     */
    @PostMapping("/index")
    public ResponseEntity<IndexResponse> indexDocument() {
        ExtractedDocument document = documentStore.get()
                .orElseThrow(() -> new NoDocumentUploadedException(
                        "No document has been uploaded yet. Please upload a PDF first via POST /api/documents/upload"));

        int indexedChunks = vectorStoreService.indexDocument(document);

        IndexResponse response = new IndexResponse(
                document.documentId(),
                document.filename(),
                indexedChunks,
                String.format("Successfully indexed %d chunks for document '%s'. Ready for vector search.",
                        indexedChunks, document.filename())
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Executes similarity search against indexed vector chunks.
     * POST /api/documents/search
     *
     * @param request SearchQueryRequest containing query, optional topK and minThreshold
     * @return SearchResponse with ranked matching chunks and citation metadata
     * @throws NoDocumentIndexedException if no document has been indexed yet (HTTP 404)
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchDocuments(
            @Valid @RequestBody SearchQueryRequest request) {

        List<SearchResultItem> results = vectorStoreService.similaritySearch(
                request.query(),
                request.topK(),
                request.minThreshold()
        );

        SearchResponse response = new SearchResponse(
                request.query(),
                vectorStoreService.getIndexedDocumentId(),
                results.size(),
                results
        );

        return ResponseEntity.ok(response);
    }
}
