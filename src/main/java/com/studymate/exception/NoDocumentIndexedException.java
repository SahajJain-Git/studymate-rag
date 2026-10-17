package com.studymate.exception;

/**
 * Thrown when a similarity search is requested, but no document has been indexed into the vector store.
 * Mapped to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class NoDocumentIndexedException extends RuntimeException {

    public NoDocumentIndexedException(String message) {
        super(message);
    }
}
