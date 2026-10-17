package com.studymate.service;

import com.studymate.exception.EmptyPdfException;
import com.studymate.exception.InvalidFileException;
import com.studymate.exception.PdfProcessingException;
import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import com.studymate.util.PdfTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PdfService.
 *
 * Concepts for Beginners:
 * - MockMultipartFile: Spring Test utility simulating file uploads in-memory without a real HTTP server.
 * - AssertJ (assertThat / assertThatThrownBy): Fluent assertion library making assertions expressive and readable.
 * - Boundary Testing: We test happy paths, edge cases (blank PDFs, corrupted streams), and negative inputs.
 */
class PdfServiceTest {

    private PdfService pdfService;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService();
    }

    @Test
    @DisplayName("Successfully extracts text and page metadata from a valid single-page PDF")
    void extractText_validSinglePage_returnsExtractedDocument() {
        String expectedText = "Polymorphism allows objects to be treated as instances of their parent class.";
        byte[] pdfBytes = PdfTestUtils.createSinglePagePdf(expectedText);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "oop-chapter1.pdf",
                "application/pdf",
                pdfBytes
        );

        ExtractedDocument result = pdfService.extractText(file);

        assertThat(result).isNotNull();
        assertThat(result.filename()).isEqualTo("oop-chapter1.pdf");
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.totalCharacters()).isGreaterThan(0);
        assertThat(result.pages()).hasSize(1);

        ExtractedPage firstPage = result.pages().get(0);
        assertThat(firstPage.pageNumber()).isEqualTo(1);
        assertThat(firstPage.text()).contains("Polymorphism");
        assertThat(firstPage.characterCount()).isEqualTo(firstPage.text().length());
    }

    @Test
    @DisplayName("Successfully extracts multi-page PDF while preserving individual page numbers and text")
    void extractText_validMultiPage_preservesPageNumbersAndText() {
        List<String> pageTexts = List.of(
                "Page 1: Introduction to Data Structures and Arrays.",
                "Page 2: Linked Lists and Pointer Manipulation.",
                "Page 3: Binary Search Trees and Graph Traversal."
        );
        byte[] pdfBytes = PdfTestUtils.createMultiPagePdf(pageTexts);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data-structures.pdf",
                "application/pdf",
                pdfBytes
        );

        ExtractedDocument result = pdfService.extractText(file);

        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.pages()).hasSize(3);

        for (int i = 0; i < 3; i++) {
            ExtractedPage page = result.pages().get(i);
            assertThat(page.pageNumber()).isEqualTo(i + 1);
            assertThat(page.text()).contains("Page " + (i + 1));
        }
    }

    @Test
    @DisplayName("Throws InvalidFileException when uploaded file is null or empty")
    void extractText_emptyFile_throwsInvalidFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> pdfService.extractText(emptyFile))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Please select a non-empty PDF file");

        assertThatThrownBy(() -> pdfService.extractText(null))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Please select a non-empty PDF file");
    }

    @Test
    @DisplayName("Throws InvalidFileException when filename does not have .pdf extension")
    void extractText_nonPdfExtension_throwsInvalidFileException() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Some plain text".getBytes()
        );

        assertThatThrownBy(() -> pdfService.extractText(textFile))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Only PDF files are supported");
    }

    @Test
    @DisplayName("Throws InvalidFileException when file content fails magic byte (%PDF-) validation")
    void extractText_invalidMagicBytes_throwsInvalidFileException() {
        // Disguised file: named .pdf, but contains plain text without %PDF- magic bytes
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                PdfTestUtils.createNonPdfFile()
        );

        assertThatThrownBy(() -> pdfService.extractText(fakePdf))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("magic byte signature mismatch");
    }

    @Test
    @DisplayName("Throws PdfProcessingException when PDF bytes are corrupted")
    void extractText_corruptedPdfBytes_throwsPdfProcessingException() {
        MockMultipartFile corruptedFile = new MockMultipartFile(
                "file",
                "corrupted.pdf",
                "application/pdf",
                PdfTestUtils.createCorruptedPdf()
        );

        assertThatThrownBy(() -> pdfService.extractText(corruptedFile))
                .isInstanceOf(PdfProcessingException.class)
                .hasMessageContaining("Failed to parse PDF document");
    }

    @Test
    @DisplayName("Throws EmptyPdfException when PDF has pages but contains zero extractable text")
    void extractText_blankPdfNoText_throwsEmptyPdfException() {
        byte[] blankPdfBytes = PdfTestUtils.createBlankPdf(2);

        MockMultipartFile blankFile = new MockMultipartFile(
                "file",
                "scanned-or-blank.pdf",
                "application/pdf",
                blankPdfBytes
        );

        assertThatThrownBy(() -> pdfService.extractText(blankFile))
                .isInstanceOf(EmptyPdfException.class)
                .hasMessageContaining("does not contain extractable text");
    }
}
