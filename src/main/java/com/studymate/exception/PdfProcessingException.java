package com.studymate.exception;

/**
 * Thrown when PDFBox encounters a fatal processing error while reading or parsing a document.
 *
 * Typical causes:
 * - PDF is encrypted or password-protected.
 * - Corrupted binary streams or malformed PDF cross-reference tables.
 * - Unexpected I/O errors during stream parsing.
 */
public class PdfProcessingException extends RuntimeException {

    public PdfProcessingException(String message) {
        super(message);
    }

    public PdfProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
