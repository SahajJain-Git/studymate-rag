package com.studymate.controller;

import com.studymate.dto.DocumentUploadResponse;
import com.studymate.dto.PageSummary;
import com.studymate.model.ExtractedDocument;
import com.studymate.service.PdfService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST Controller providing endpoints for document upload and ingestion.
 *
 * Concepts for Beginners:
 * - Thin Controller Pattern: This class contains NO parsing, extraction, or validation algorithms.
 *   It simply receives the HTTP multipart request, delegates immediately to PdfService,
 *   and maps the domain result into a clean API response DTO.
 * - @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE): Tells Spring MVC to expect
 *   a multipart HTTP request (RFC 7578) with form field 'file'.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final PdfService pdfService;

    public DocumentController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    /**
     * Uploads and extracts text from a PDF study document.
     * POST /api/documents/upload
     *
     * @param file The multipart PDF file to process
     * @return DocumentUploadResponse containing page count, character count, and page summaries
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        // 1. Delegate business logic to service layer
        ExtractedDocument extracted = pdfService.extractText(file);

        // 2. Map domain entity to API response DTO (with friendly page previews)
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
                String.format("Successfully parsed %d pages (%d characters). Ready for chunking.",
                        extracted.totalPages(), extracted.totalCharacters())
        );

        return ResponseEntity.ok(response);
    }
}
