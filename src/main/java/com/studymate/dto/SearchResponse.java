package com.studymate.dto;

import java.util.List;

/**
 * Response DTO returned by POST /api/documents/search containing ranked similarity matches.
 *
 * @param query        The executed search query
 * @param documentId   ID of the currently indexed document searched
 * @param totalMatches Number of matching chunks returned
 * @param results      Ranked list of SearchResultItem records
 */
public record SearchResponse(
        String query,
        String documentId,
        int totalMatches,
        List<SearchResultItem> results
) {
}
