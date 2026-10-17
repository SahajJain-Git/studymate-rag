package com.studymate.service;

import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InMemoryDocumentStore.
 *
 * Verifies document retention, single-document replacement, and clearing behavior.
 */
class InMemoryDocumentStoreTest {

    private InMemoryDocumentStore documentStore;

    @BeforeEach
    void setUp() {
        documentStore = new InMemoryDocumentStore();
    }

    @Test
    @DisplayName("Returns empty Optional when no document has been stored")
    void get_whenEmpty_returnsEmptyOptional() {
        assertThat(documentStore.hasDocument()).isFalse();
        assertThat(documentStore.get()).isEmpty();
    }

    @Test
    @DisplayName("Saves and retrieves an active ExtractedDocument")
    void saveAndGet_retainsDocument() {
        ExtractedDocument doc = new ExtractedDocument(
                "operating-systems.pdf",
                1,
                50,
                List.of(new ExtractedPage(1, "Virtual memory and page replacement algorithms.", 47))
        );

        documentStore.save(doc);

        assertThat(documentStore.hasDocument()).isTrue();
        Optional<ExtractedDocument> retrieved = documentStore.get();
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().filename()).isEqualTo("operating-systems.pdf");
        assertThat(retrieved.get().totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Uploading a new document replaces the previously stored document")
    void save_replacesExistingDocument() {
        ExtractedDocument firstDoc = new ExtractedDocument(
                "first.pdf",
                1,
                20,
                List.of(new ExtractedPage(1, "First document text.", 20))
        );
        ExtractedDocument secondDoc = new ExtractedDocument(
                "second.pdf",
                2,
                40,
                List.of(
                        new ExtractedPage(1, "Second document page 1.", 23),
                        new ExtractedPage(2, "Second document page 2.", 23)
                )
        );

        documentStore.save(firstDoc);
        assertThat(documentStore.get().get().filename()).isEqualTo("first.pdf");

        // Overwrite with second document
        documentStore.save(secondDoc);
        assertThat(documentStore.get().get().filename()).isEqualTo("second.pdf");
        assertThat(documentStore.get().get().totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Clearing store removes document reference")
    void clear_removesDocument() {
        ExtractedDocument doc = new ExtractedDocument(
                "temp.pdf",
                1,
                10,
                List.of(new ExtractedPage(1, "Some text.", 10))
        );

        documentStore.save(doc);
        assertThat(documentStore.hasDocument()).isTrue();

        documentStore.clear();
        assertThat(documentStore.hasDocument()).isFalse();
        assertThat(documentStore.get()).isEmpty();
    }
}
