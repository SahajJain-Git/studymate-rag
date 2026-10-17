package com.studymate.service;

import com.studymate.ai.DeterministicLocalEmbeddingModel;
import com.studymate.dto.SearchResultItem;
import com.studymate.exception.EmptyPdfException;
import com.studymate.exception.NoDocumentIndexedException;
import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VectorStoreService Unit Tests")
class VectorStoreServiceTest {

    private VectorStore vectorStore;
    private ChunkingService chunkingService;
    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        DeterministicLocalEmbeddingModel embeddingModel = new DeterministicLocalEmbeddingModel();
        vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        chunkingService = new ChunkingService(200, 20);
        vectorStoreService = new VectorStoreService(vectorStore, chunkingService);
    }

    @Test
    @DisplayName("Should index document, preserve metadata, and enable similarity retrieval")
    void shouldIndexDocumentAndPreserveMetadata() {
        ExtractedDocument document = new ExtractedDocument(
                "doc-101",
                "operating_systems.pdf",
                2,
                400,
                List.of(
                        new ExtractedPage(1, "Virtual memory management allows processes to access contiguous address spaces.", 78),
                        new ExtractedPage(2, "Deadlock avoidance algorithms such as Dijkstra's Banker algorithm ensure safety.", 79)
                )
        );

        int indexedCount = vectorStoreService.indexDocument(document);

        assertTrue(indexedCount >= 2, "Expected at least 2 chunks to be indexed");
        assertTrue(vectorStoreService.hasIndexedDocument());
        assertEquals("doc-101", vectorStoreService.getIndexedDocumentId());
        assertEquals(indexedCount, vectorStoreService.getIndexedChunkCount());

        // Perform similarity search
        List<SearchResultItem> results = vectorStoreService.similaritySearch("virtual memory address spaces", 4, 0.0);

        assertFalse(results.isEmpty(), "Should retrieve matching chunks");
        SearchResultItem topResult = results.get(0);

        assertEquals("doc-101", topResult.documentId());
        assertEquals("operating_systems.pdf", topResult.sourceFilename());
        assertEquals(1, topResult.pageNumber());
        assertTrue(topResult.score() > 0.0, "Score should be positive");
        assertTrue(topResult.text().contains("Virtual memory"));
    }

    @Test
    @DisplayName("Should throw NoDocumentIndexedException when searching an unindexed store")
    void shouldThrowExceptionWhenSearchingUnindexedStore() {
        assertThrows(NoDocumentIndexedException.class, () ->
                vectorStoreService.similaritySearch("any query", 5, 0.0)
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when indexing null or empty document")
    void shouldRejectNullOrEmptyDocumentOnIndex() {
        assertThrows(IllegalArgumentException.class, () ->
                vectorStoreService.indexDocument(null)
        );

        ExtractedDocument emptyDoc = new ExtractedDocument("doc-empty", "empty.pdf", 0, 0, List.of());
        assertThrows(IllegalArgumentException.class, () ->
                vectorStoreService.indexDocument(emptyDoc)
        );
    }

    @Test
    @DisplayName("Should throw EmptyPdfException when document has no extractable text chunks")
    void shouldThrowEmptyPdfExceptionWhenNoChunks() {
        ExtractedDocument blankDoc = new ExtractedDocument(
                "doc-blank",
                "blank.pdf",
                1,
                0,
                List.of(new ExtractedPage(1, "    \n\t  ", 0))
        );

        assertThrows(EmptyPdfException.class, () ->
                vectorStoreService.indexDocument(blankDoc)
        );
    }

    @Test
    @DisplayName("Should evict stale chunks when a new document is indexed (prevent ghost vectors)")
    void shouldEvictStaleChunksWhenNewDocumentIsIndexed() {
        // Document A: Operating Systems
        ExtractedDocument docA = new ExtractedDocument(
                "doc-A",
                "os.pdf",
                1,
                150,
                List.of(new ExtractedPage(1, "Kernel scheduling and thread concurrency.", 41))
        );
        vectorStoreService.indexDocument(docA);
        assertEquals("doc-A", vectorStoreService.getIndexedDocumentId());

        // Verify docA is searchable
        List<SearchResultItem> resultsA = vectorStoreService.similaritySearch("Kernel scheduling", 5, 0.0);
        assertFalse(resultsA.isEmpty());
        assertEquals("doc-A", resultsA.get(0).documentId());

        // Document B: Database Systems
        ExtractedDocument docB = new ExtractedDocument(
                "doc-B",
                "db.pdf",
                1,
                150,
                List.of(new ExtractedPage(1, "Relational normalization, B-trees, and ACID transactions.", 55))
        );
        vectorStoreService.indexDocument(docB);
        assertEquals("doc-B", vectorStoreService.getIndexedDocumentId());

        // Search for docA query; docA's chunks should be completely evicted from vector store!
        List<SearchResultItem> resultsAfter = vectorStoreService.similaritySearch("Kernel scheduling", 5, 0.0);
        for (SearchResultItem item : resultsAfter) {
            assertEquals("doc-B", item.documentId(), "No chunks from doc-A should remain in the index");
        }
    }

    @Test
    @DisplayName("Should maintain consistency if vector storage fails during indexing")
    void shouldPreservePreviousStateWhenIndexingFails() {
        VectorStore failStore = new FailingVectorStore();
        VectorStoreService failService = new VectorStoreService(failStore, chunkingService);

        ExtractedDocument doc = new ExtractedDocument(
                "doc-fail",
                "test.pdf",
                1,
                100,
                List.of(new ExtractedPage(1, "Some sample text content.", 25))
        );

        assertThrows(RuntimeException.class, () -> failService.indexDocument(doc));

        // State must remain unindexed
        assertFalse(failService.hasIndexedDocument());
        assertEquals(0, failService.getIndexedChunkCount());
    }

    /**
     * Minimal pure-Java test stub that simulates a failure in VectorStore.add().
     * Avoids bytecode instrumentation limitations on cutting-edge JVMs.
     */
    private static class FailingVectorStore implements VectorStore {
        @Override
        public void add(List<org.springframework.ai.document.Document> documents) {
            throw new RuntimeException("Simulated VectorStore connection failure");
        }

        @Override
        public void delete(List<String> idList) {}

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {}

        @Override
        public List<org.springframework.ai.document.Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
            return List.of();
        }
    }

    @Test
    @DisplayName("Should clear store correctly and reset tracking state")
    void shouldClearStoreCorrectly() {
        ExtractedDocument doc = new ExtractedDocument(
                "doc-clear",
                "test.pdf",
                1,
                100,
                List.of(new ExtractedPage(1, "Some clearable text content.", 27))
        );
        vectorStoreService.indexDocument(doc);
        assertTrue(vectorStoreService.hasIndexedDocument());

        vectorStoreService.clearStore();

        assertFalse(vectorStoreService.hasIndexedDocument());
        assertEquals(0, vectorStoreService.getIndexedChunkCount());
        assertThrows(NoDocumentIndexedException.class, () ->
                vectorStoreService.similaritySearch("test", 3, 0.0)
        );
    }

    @Test
    @DisplayName("Should reject blank query in similaritySearch")
    void shouldRejectBlankQuery() {
        ExtractedDocument doc = new ExtractedDocument(
                "doc-valid",
                "valid.pdf",
                1,
                100,
                List.of(new ExtractedPage(1, "Valid document text.", 20))
        );
        vectorStoreService.indexDocument(doc);

        assertThrows(IllegalArgumentException.class, () ->
                vectorStoreService.similaritySearch("   ", 3, 0.0)
        );
    }
}
