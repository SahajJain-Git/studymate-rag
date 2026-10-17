package com.studymate.dto;

import java.util.List;

/**
 * Response DTO returned to the client upon successful PDF upload and text extraction.
 *
 * Concepts for Beginners:
 * - Keeps controllers thin: The controller passes the extracted domain object to this DTO,
 *   which packages only what the client needs to see.
 * - Sensitive raw contents are not dumped in their entirety; page counts, character metrics,
 *   and short previews are returned.
 *
 * @param filename        Name of the processed file
 * @param totalPages      Total pages extracted
 * @param totalCharacters Total text characters extracted across all pages
 * @param pages           List of page summaries with character metrics
 * @param message         Human-readable confirmation message
 */
public record DocumentUploadResponse(
        String filename,
        int totalPages,
        int totalCharacters,
        List<PageSummary> pages,
        String message
) {
}
