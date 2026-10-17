package com.studymate.dto;

/**
 * Summary metadata for an individual page returned in the upload response.
 *
 * @param pageNumber     1-based page number
 * @param characterCount Number of characters extracted from this page
 * @param preview        A short preview snippet (first ~120 characters) of the page text
 */
public record PageSummary(
        int pageNumber,
        int characterCount,
        String preview
) {
}
