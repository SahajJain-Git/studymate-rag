package com.studymate.model;

/**
 * Represents the extracted text and metadata of a single PDF page.
 *
 * Concepts for Beginners:
 * - A Java 'record' is immutable. Once extracted, a page's content should never be modified.
 * - Storing text per page (with 1-based page numbers) is crucial for RAG pipelines:
 *   when the AI generates an answer in later stages, it can cite exact page numbers (e.g., "[Page 3]").
 *
 * @param pageNumber     1-based page index from the source PDF
 * @param text           Sanitized text extracted from this page
 * @param characterCount Total number of characters on this page
 */
public record ExtractedPage(
        int pageNumber,
        String text,
        int characterCount
) {
    public ExtractedPage {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Page number must be 1-based and positive.");
        }
        text = (text == null) ? "" : text;
        characterCount = text.length();
    }
}
