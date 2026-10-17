package com.studymate.service;

import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import com.studymate.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ChunkingService.
 *
 * Verifies chunk size boundaries, sliding window overlap accuracy, page-number preservation,
 * and configuration validation.
 */
class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        // Default configuration: chunkSize = 500, overlap = 50
        chunkingService = new ChunkingService(500, 50);
    }

    @Test
    @DisplayName("Short text shorter than chunkSize produces exactly 1 chunk")
    void chunkDocument_shortText_createsSingleChunk() {
        String shortText = "Encapsulation bundles data and methods that operate on that data.";
        ExtractedDocument doc = new ExtractedDocument(
                "oop.pdf",
                1,
                shortText.length(),
                List.of(new ExtractedPage(1, shortText, shortText.length()))
        );

        List<TextChunk> chunks = chunkingService.chunkDocument(doc);

        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        assertThat(chunk.chunkIndex()).isEqualTo(0);
        assertThat(chunk.pageNumber()).isEqualTo(1);
        assertThat(chunk.sourceFilename()).isEqualTo("oop.pdf");
        assertThat(chunk.text()).isEqualTo(shortText);
        assertThat(chunk.characterCount()).isEqualTo(shortText.length());
        assertThat(chunk.chunkId()).isEqualTo("oop.pdf-p1-c0");
    }

    @Test
    @DisplayName("Long text produces multiple chunks with consistent sliding overlap")
    void chunkDocument_longText_appliesSlidingWindowAndOverlap() {
        // Create 1000 characters of predictable text (e.g. repeated numbers or words)
        // With chunkSize=500 and overlap=50, step=450.
        // Chunk 0: [0, 500]
        // Chunk 1: [450, 950]
        // Chunk 2: [900, 1000]
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append(String.format("Word%03d ", i)); // 8 chars per word * 100 = 800 chars
        }
        while (builder.length() < 1000) {
            builder.append("A");
        }
        String text1000 = builder.substring(0, 1000);

        ExtractedDocument doc = new ExtractedDocument(
                "guide.pdf",
                1,
                1000,
                List.of(new ExtractedPage(1, text1000, 1000))
        );

        List<TextChunk> chunks = chunkingService.chunkDocument(doc);

        assertThat(chunks).hasSize(3);

        TextChunk c0 = chunks.get(0);
        TextChunk c1 = chunks.get(1);
        TextChunk c2 = chunks.get(2);

        assertThat(c0.characterCount()).isEqualTo(500);
        assertThat(c1.characterCount()).isEqualTo(500);
        assertThat(c2.characterCount()).isEqualTo(100); // 1000 - 900 = 100

        // Overlap verification: The end of chunk 0 must match the start of chunk 1 by 50 characters
        String c0Suffix = c0.text().substring(500 - 50);
        String c1Prefix = c1.text().substring(0, 50);
        assertThat(c1Prefix).isEqualTo(c0Suffix);

        // Overlap verification: The end of chunk 1 must match the start of chunk 2 by 50 characters
        // c1 start was 450, end 950. c2 start was 900.
        // The overlap between [450, 950] and [900, 1000] is [900, 950] (50 characters)
        String c1Suffix = c1.text().substring(500 - 50);
        String c2Prefix = c2.text().substring(0, 50);
        assertThat(c2Prefix).isEqualTo(c1Suffix);
    }

    @Test
    @DisplayName("Multi-page document preserves distinct page numbers for citations")
    void chunkDocument_multiPage_preservesPageNumbers() {
        String page1 = "Page 1 Content: Fundamental Concepts.";
        String page2 = "Page 2 Content: Intermediate Implementations.";
        String page3 = "Page 3 Content: Advanced Distributed Systems.";

        ExtractedDocument doc = new ExtractedDocument(
                "textbook.pdf",
                3,
                page1.length() + page2.length() + page3.length(),
                List.of(
                        new ExtractedPage(1, page1, page1.length()),
                        new ExtractedPage(2, page2, page2.length()),
                        new ExtractedPage(3, page3, page3.length())
                )
        );

        List<TextChunk> chunks = chunkingService.chunkDocument(doc);

        assertThat(chunks).hasSize(3);

        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(0).text()).isEqualTo(page1);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);

        assertThat(chunks.get(1).pageNumber()).isEqualTo(2);
        assertThat(chunks.get(1).text()).isEqualTo(page2);
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);

        assertThat(chunks.get(2).pageNumber()).isEqualTo(3);
        assertThat(chunks.get(2).text()).isEqualTo(page3);
        assertThat(chunks.get(2).chunkIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("Empty or null document returns empty chunk list")
    void chunkDocument_emptyOrNull_returnsEmptyList() {
        assertThat(chunkingService.chunkDocument(null)).isEmpty();

        ExtractedDocument emptyDoc = new ExtractedDocument("empty.pdf", 0, 0, List.of());
        assertThat(chunkingService.chunkDocument(emptyDoc)).isEmpty();
    }

    @Test
    @DisplayName("Invalid configurations throw IllegalArgumentException")
    void invalidConfiguration_throwsIllegalArgumentException() {
        // Case 1: chunkSize <= 0
        assertThatThrownBy(() -> new ChunkingService(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size must be greater than 0");

        assertThatThrownBy(() -> new ChunkingService(-50, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size must be greater than 0");

        // Case 2: overlap < 0
        assertThatThrownBy(() -> new ChunkingService(500, -10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap must be non-negative");

        // Case 3: overlap >= chunkSize
        assertThatThrownBy(() -> new ChunkingService(500, 500))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be strictly less than chunk-size");

        assertThatThrownBy(() -> new ChunkingService(500, 600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be strictly less than chunk-size");
    }

    @Test
    @DisplayName("Custom chunk-size and overlap work deterministically")
    void chunkText_customSizes_worksDeterministically() {
        // 250 characters, chunkSize=100, overlap=20 -> step=80
        // Chunk 0: [0, 100]
        // Chunk 1: [80, 180]
        // Chunk 2: [160, 250]
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        String text250 = alphabet.repeat(10).substring(0, 250);

        List<TextChunk> chunks = chunkingService.chunkText(
                text250,
                5,
                "custom.pdf",
                100,
                20,
                new AtomicInteger(0)
        );

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(5);
        assertThat(chunks.get(0).characterCount()).isEqualTo(100);
        assertThat(chunks.get(1).characterCount()).isEqualTo(100);
        assertThat(chunks.get(2).characterCount()).isEqualTo(90); // 250 - 160 = 90
    }
}
