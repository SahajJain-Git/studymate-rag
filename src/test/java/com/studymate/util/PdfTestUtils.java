package com.studymate.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Utility helper for generating real in-memory PDF byte streams during tests.
 *
 * Concepts for Beginners:
 * - Deterministic Testing: Rather than maintaining static sample PDF binary files in src/test/resources,
 *   generating PDFs dynamically in code ensures tests are self-contained, reproducible,
 *   and fast with zero external disk dependencies.
 * - Resource Safety: PDDocument and PDPageContentStream implement AutoCloseable and must be closed
 *   to ensure streams are flushed correctly.
 */
public final class PdfTestUtils {

    private PdfTestUtils() {
        // Private constructor for static utility class
    }

    /**
     * Creates a valid single-page PDF containing the specified text string.
     */
    public static byte[] createSinglePagePdf(String text) {
        return createMultiPagePdf(List.of(text));
    }

    /**
     * Creates a valid multi-page PDF where each string in pageTexts forms its own page.
     */
    public static byte[] createMultiPagePdf(List<String> pageTexts) {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);

                if (text != null && !text.isEmpty()) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.beginText();
                        contentStream.setFont(font, 12);
                        contentStream.newLineAtOffset(50, 700);
                        contentStream.showText(text);
                        contentStream.endText();
                    }
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate in-memory test PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a PDF with blank pages (contains pages, but no text streams).
     */
    public static byte[] createBlankPdf(int pageCount) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate blank test PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a byte array starting with the valid PDF magic header (%PDF-),
     * followed by corrupted, unparseable binary garbage.
     */
    public static byte[] createCorruptedPdf() {
        byte[] header = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] garbage = "CORRUPTED_STREAM_NON_PARSEABLE_DATA_12345!@#$%^&*()_+".getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[header.length + garbage.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(garbage, 0, result, header.length, garbage.length);
        return result;
    }

    /**
     * Creates a non-PDF byte array (e.g. text file or plain binary bytes).
     */
    public static byte[] createNonPdfFile() {
        return "This is just plain text, not a valid PDF file!".getBytes(StandardCharsets.UTF_8);
    }
}
