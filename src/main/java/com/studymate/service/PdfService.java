package com.studymate.service;

import com.studymate.exception.EmptyPdfException;
import com.studymate.exception.InvalidFileException;
import com.studymate.exception.PdfProcessingException;
import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for validating, parsing, and extracting page-level text from PDF files.
 *
 * Concepts for Beginners:
 * - @Service: Marks this class as a Spring-managed service bean containing business logic.
 * - Inversion of Control (IoC): The controller never calls `new PdfService()`; Spring instantiates
 *   and injects this singleton into the controller.
 * - Resource Management: Uses try-with-resources with Apache PDFBox 3's `PDDocument` to guarantee
 *   native memory and stream cleanup without leaking resources.
 * - Privacy & Security: Document text contents are never written to disk or printed in application logs.
 */
@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    // Standard PDF magic byte signature: %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
    private static final byte[] PDF_MAGIC_BYTES = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    /**
     * Validates an uploaded MultipartFile and extracts structured text preserving page numbers.
     *
     * @param file The uploaded multipart file
     * @return ExtractedDocument containing document-level metrics and page-level text
     * @throws InvalidFileException   if file is missing, not a PDF, or exceeds limits
     * @throws EmptyPdfException      if the PDF is valid syntax but contains no extractable text
     * @throws PdfProcessingException if PDFBox fails to parse or the document is encrypted
     */
    public ExtractedDocument extractText(MultipartFile file) {
        validateFile(file);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        long fileSize = file.getSize();

        log.info("Starting PDF text extraction for file: '{}' (size: {} bytes)", originalFilename, fileSize);

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidFileException("Failed to read uploaded file bytes: " + e.getMessage(), e);
        }

        // Deep Content Inspection: Magic byte verification (Do not trust filename or content type alone)
        validatePdfMagicBytes(fileBytes);

        // Load and parse PDF using Apache PDFBox 3.0.x Loader API
        try (PDDocument document = Loader.loadPDF(fileBytes)) {

            if (document.isEncrypted()) {
                throw new PdfProcessingException("Password-protected or encrypted PDF documents are not supported.");
            }

            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new EmptyPdfException("The uploaded PDF document contains 0 pages.");
            }

            PDFTextStripper textStripper = new PDFTextStripper();
            // Sorting by position ensures text is extracted in natural visual reading order
            textStripper.setSortByPosition(true);

            List<ExtractedPage> extractedPages = new ArrayList<>(totalPages);
            int totalExtractedChars = 0;

            for (int pageNo = 1; pageNo <= totalPages; pageNo++) {
                textStripper.setStartPage(pageNo);
                textStripper.setEndPage(pageNo);

                String rawPageText = textStripper.getText(document);
                String cleanPageText = sanitizeText(rawPageText);

                extractedPages.add(new ExtractedPage(pageNo, cleanPageText, cleanPageText.length()));
                totalExtractedChars += cleanPageText.length();
            }

            // Reject PDFs with zero extractable text (e.g. scanned images without OCR)
            if (totalExtractedChars == 0) {
                throw new EmptyPdfException("The uploaded PDF does not contain extractable text. " +
                        "Scanned image-only PDFs without an OCR text layer are not supported.");
            }

            String documentId = "doc-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            log.info("Successfully extracted {} characters across {} pages for documentId: '{}', file: '{}'",
                    totalExtractedChars, totalPages, documentId, originalFilename);

            return new ExtractedDocument(documentId, originalFilename, totalPages, totalExtractedChars, extractedPages);

        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            log.warn("Uploaded PDF '{}' is password-protected/encrypted: {}", originalFilename, e.getMessage());
            throw new PdfProcessingException("Password-protected or encrypted PDF documents are not supported.", e);
        } catch (EmptyPdfException | InvalidFileException | PdfProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse PDF document '{}': {}", originalFilename, e.getMessage());
            throw new PdfProcessingException("Failed to parse PDF document: " + e.getMessage(), e);
        }
    }


    /**
     * Validates existence, non-emptiness, and filename extension of the uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select a non-empty PDF file to upload.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new InvalidFileException("Uploaded file must have a valid filename.");
        }

        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidFileException("Only PDF files are supported. File must have a .pdf extension.");
        }
    }

    /**
     * Verifies that the byte stream begins with the valid PDF magic header (%PDF-).
     */
    private void validatePdfMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_MAGIC_BYTES.length) {
            throw new InvalidFileException("Uploaded file is too small to be a valid PDF document.");
        }

        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (bytes[i] != PDF_MAGIC_BYTES[i]) {
                throw new InvalidFileException("File header is not a valid PDF document (magic byte signature mismatch).");
            }
        }
    }

    /**
     * Sanitizes extracted text by removing null bytes, normalizing line separators,
     * and trimming unnecessary whitespace artifacts common in PDF text extraction.
     */
    private String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")       // Remove null characters
                .replace("\r\n", "\n")          // Normalize CRLF to LF
                .replace("\r", "\n")            // Normalize CR to LF
                .replaceAll("[ \t]+", " ")       // Collapse multiple spaces/tabs into single space
                .replaceAll("\n{3,}", "\n\n")   // Limit consecutive blank lines to 2
                .trim();
    }

    /**
     * Sanitizes filenames to prevent path traversal attempts (e.g. "../../../file.pdf").
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unknown.pdf";
        }
        // Extract base name only, stripping any directory path
        int lastUnixSlash = filename.lastIndexOf('/');
        int lastWinSlash = filename.lastIndexOf('\\');
        int lastSlash = Math.max(lastUnixSlash, lastWinSlash);
        if (lastSlash >= 0 && lastSlash < filename.length() - 1) {
            return filename.substring(lastSlash + 1);
        }
        return filename;
    }
}
