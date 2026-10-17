package com.studymate.exception;

/**
 * Thrown when an operation requires an active document in memory, but none has been uploaded yet.
 * Mapped to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class NoDocumentUploadedException extends RuntimeException {

    public NoDocumentUploadedException(String message) {
        super(message);
    }
}
