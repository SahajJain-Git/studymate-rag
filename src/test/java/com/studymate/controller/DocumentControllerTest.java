package com.studymate.controller;

import com.studymate.util.PdfTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DocumentController and GlobalExceptionHandler.
 *
 * Concepts for Beginners:
 * - MockMvc: Simulates HTTP multipart requests against the DispatcherServlet.
 * - End-to-End MVC Slice: Verifies that routing, multipart resolving, exception handling,
 *   and JSON serialization work together in the Spring ApplicationContext.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/documents/upload with valid PDF returns 200 OK and extraction metadata")
    void upload_validPdf_returns200AndUploadResponse() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createMultiPagePdf(List.of(
                "Chapter 1: Principles of Clean Architecture and Domain Modeling.",
                "Chapter 2: Implementing REST APIs with Spring Web."
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "software-architecture.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.filename").value("software-architecture.pdf"))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalCharacters").isNumber())
                .andExpect(jsonPath("$.pages").isArray())
                .andExpect(jsonPath("$.pages.length()").value(2))
                .andExpect(jsonPath("$.pages[0].pageNumber").value(1))
                .andExpect(jsonPath("$.pages[0].preview").isNotEmpty())
                .andExpect(jsonPath("$.pages[1].pageNumber").value(2))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/documents/upload with empty file returns 400 Bad Request and ErrorResponse")
    void upload_emptyFile_returns400AndErrorResponse() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Please select a non-empty PDF file to upload."))
                .andExpect(jsonPath("$.path").value("/api/documents/upload"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/documents/upload with non-PDF extension returns 400 Bad Request")
    void upload_nonPdfExtension_returns400() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "syllabus.txt",
                "text/plain",
                "Course schedule".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(textFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Only PDF files are supported. File must have a .pdf extension."));
    }

    @Test
    @DisplayName("POST /api/documents/upload with invalid magic bytes returns 400 Bad Request")
    void upload_invalidMagicBytes_returns400() throws Exception {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file",
                "disguised.pdf",
                "application/pdf",
                PdfTestUtils.createNonPdfFile()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(fakePdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("magic byte signature mismatch")));
    }

    @Test
    @DisplayName("POST /api/documents/upload with corrupted PDF returns 422 Unprocessable Entity")
    void upload_corruptedPdf_returns422() throws Exception {
        MockMultipartFile corruptedPdf = new MockMultipartFile(
                "file",
                "damaged.pdf",
                "application/pdf",
                PdfTestUtils.createCorruptedPdf()
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(corruptedPdf))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value(containsString("Failed to parse PDF document")));
    }

    @Test
    @DisplayName("POST /api/documents/upload with blank textless PDF returns 422 Unprocessable Entity")
    void upload_blankPdf_returns422() throws Exception {
        MockMultipartFile blankPdf = new MockMultipartFile(
                "file",
                "blank.pdf",
                "application/pdf",
                PdfTestUtils.createBlankPdf(1)
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(blankPdf))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(containsString("does not contain extractable text")));
    }

    @Test
    @DisplayName("POST /api/documents/upload with missing 'file' parameter returns 400 Bad Request")
    void upload_missingFileParameter_returns400() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request parameter 'file' is missing."));
    }
}
