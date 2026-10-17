package com.studymate.model;

import java.util.List;
import java.util.UUID;

/**
 * Represents the entire extracted content of a parsed PDF document.
 *
 * Concepts for Beginners:
 * - documentId: A unique identifier generated once per upload (e.g. "doc-a1b2c3d4").
 *   This ID links all downstream text chunks and vector embeddings to a specific upload session,
 *   preventing chunk ID collisions when documents with identical filenames are uploaded.
 * - List.copyOf(): Creates an unmodifiable defensive copy of the pages list,
 *   preventing external classes from mutating internal state after construction.
 *
 * @param documentId      Unique upload session identifier for this document
 * @param filename        Original name of the uploaded PDF file
 * @param totalPages      Total number of pages discovered in the PDF
 * @param totalCharacters Total number of extracted text characters across all pages
 * @param pages           List of extracted pages with page-level text
 */
public record ExtractedDocument(
        String documentId,
        String filename,
        int totalPages,
        int totalCharacters,
        List<ExtractedPage> pages
) {
    public ExtractedDocument {
        documentId = (documentId == null || documentId.isBlank())
                ? "doc-" + UUID.randomUUID().toString().substring(0, 8)
                : documentId;
        pages = (pages == null) ? List.of() : List.copyOf(pages);
        totalPages = pages.size();
        totalCharacters = pages.stream().mapToInt(ExtractedPage::characterCount).sum();
    }

    /**
     * Backward-compatible constructor that automatically generates a unique documentId.
     */
    public ExtractedDocument(String filename, int totalPages, int totalCharacters, List<ExtractedPage> pages) {
        this("doc-" + UUID.randomUUID().toString().substring(0, 8), filename, totalPages, totalCharacters, pages);
    }
}
