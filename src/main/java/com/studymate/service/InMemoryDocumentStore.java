package com.studymate.service;

import com.studymate.model.ExtractedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, Spring-managed in-memory store for the currently active ExtractedDocument.
 *
 * Concepts for Beginners:
 * - @Component: Marks this class as a Spring-managed singleton bean. Spring creates one instance
 *   at startup and shares it across all controllers and services.
 * - AtomicReference: A thread-safe container for an object reference from java.util.concurrent.
 *   Because multiple HTTP requests can execute concurrently on Tomcat's thread pool, AtomicReference
 *   guarantees that reading and writing the active document is atomic and thread-safe without explicit locks.
 * - Lifecycle & Scope:
 *   1. Exactly one active document is held at a time.
 *   2. Uploading a new PDF replaces any previously held document.
 *   3. If the Spring Boot application restarts, this in-memory store is reset to empty.
 */
@Component
public class InMemoryDocumentStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDocumentStore.class);

    private final AtomicReference<ExtractedDocument> currentDocument = new AtomicReference<>();

    /**
     * Stores the latest extracted document, replacing any previously stored document.
     *
     * @param document The newly extracted PDF document to store
     */
    public void save(ExtractedDocument document) {
        if (document != null) {
            this.currentDocument.set(document);
            log.info("Saved document '{}' ({} pages, {} characters) to InMemoryDocumentStore.",
                    document.filename(), document.totalPages(), document.totalCharacters());
        }
    }

    /**
     * Retrieves the currently active document, if one has been uploaded.
     *
     * @return An Optional containing the active ExtractedDocument, or Optional.empty() if none exists
     */
    public Optional<ExtractedDocument> get() {
        return Optional.ofNullable(this.currentDocument.get());
    }

    /**
     * Checks whether an active document is currently loaded.
     *
     * @return true if a document is present, false otherwise
     */
    public boolean hasDocument() {
        return this.currentDocument.get() != null;
    }

    /**
     * Clears the current document from memory.
     * Useful for testing and cleanup.
     */
    public void clear() {
        ExtractedDocument previous = this.currentDocument.getAndSet(null);
        if (previous != null) {
            log.info("Cleared document '{}' from InMemoryDocumentStore.", previous.filename());
        }
    }
}
