package com.studymate.exception;

/**
 * Thrown when a PDF is technically valid in syntax, but contains zero extractable text.
 *
 * Typical causes:
 * - Scanned image-only PDFs without OCR text layers.
 * - Completely blank pages.
 * - Documents where text was converted to raster graphics or vector outlines.
 *
 * This distinct exception informs the client (and future LLM steps) that no semantic text
 * is available for chunking or question-answering.
 */
public class EmptyPdfException extends RuntimeException {

    public EmptyPdfException(String message) {
        super(message);
    }

    public EmptyPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
