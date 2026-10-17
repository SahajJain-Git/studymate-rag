package com.studymate.model;

import java.util.List;

/**
 * Represents the entire extracted content of a parsed PDF document.
 *
 * Concepts for Beginners:
 * - List.copyOf(): Creates an unmodifiable defensive copy of the pages list,
 *   preventing external classes from mutating internal state after construction.
 * - This domain model bridges the gap between the low-level PDF parsing engine
 *   and future business services (chunking, vectorization, and REST controllers).
 *
 * @param filename        Original name of the uploaded PDF file
 * @param totalPages      Total number of pages discovered in the PDF
 * @param totalCharacters Total number of extracted text characters across all pages
 * @param pages           List of extracted pages with page-level text
 */
public record ExtractedDocument(
        String filename,
        int totalPages,
        int totalCharacters,
        List<ExtractedPage> pages
) {
    public ExtractedDocument {
        pages = (pages == null) ? List.of() : List.copyOf(pages);
        totalPages = pages.size();
        totalCharacters = pages.stream().mapToInt(ExtractedPage::characterCount).sum();
    }
}
