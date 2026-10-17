package com.studymate.controller;

import com.studymate.service.InMemoryDocumentStore;
import com.studymate.service.VectorStoreService;
import com.studymate.util.PdfTestUtils;
import org.junit.jupiter.api.BeforeEach;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for QueryController, RagService, and the RAG question answering pipeline.
 *
 * Concepts for Beginners:
 * - End-to-End Pipeline Verification: Upload PDF -> Index Chunks -> Ask Question -> Assert Citations.
 * - Test Isolation: The @BeforeEach method purges both the document store and vector store
 *   between tests so state from one test never affects another.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDocumentStore documentStore;

    @Autowired
    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        documentStore.clear();
        vectorStoreService.clearStore();
    }

    @Test
    @DisplayName("POST /api/query returns 404 Not Found when no document has been indexed")
    void askQuestion_whenNoDocumentIndexed_returns404() throws Exception {
        String requestJson = """
                {
                    "question": "What is object-oriented programming?"
                }
                """;

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("No document has been indexed yet")));
    }

    @Test
    @DisplayName("POST /api/query with blank question returns 400 Bad Request")
    void askQuestion_blankQuestion_returns400() throws Exception {
        String requestJson = """
                {
                    "question": "   "
                }
                """;

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Question must not be blank.")));
    }

    @Test
    @DisplayName("POST /api/query after upload and indexing returns 200 OK with answer and citations")
    void askQuestion_afterUploadAndIndex_returns200AndAnswer() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createMultiPagePdf(List.of(
                "Page 1: Principles of Object-Oriented Programming. Encapsulation hides internal object state.",
                "Page 2: Inheritance and polymorphism allow code reuse and dynamic method dispatch."
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "oop-study-guide.pdf",
                "application/pdf",
                pdfBytes
        );

        // 1. Upload document
        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk());

        // 2. Index document
        mockMvc.perform(post("/api/documents/index"))
                .andExpect(status().isOk());

        // 3. Ask question
        String requestJson = """
                {
                    "question": "What is encapsulation in object-oriented programming?",
                    "topK": 2,
                    "minThreshold": 0.0
                }
                """;

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value("What is encapsulation in object-oriented programming?"))
                .andExpect(jsonPath("$.answer").value(containsString("Page 1")))
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.hasSufficientContext").value(true))
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(1))
                .andExpect(jsonPath("$.citations[0].sourceFilename").value("oop-study-guide.pdf"))
                .andExpect(jsonPath("$.citations[0].chunkId").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].textPreview").value(containsString("Encapsulation hides")))
                .andExpect(jsonPath("$.citations[0].score").isNumber());
    }

    @Test
    @DisplayName("POST /api/query returns 200 with hasSufficientContext=false when retrieval finds no chunks")
    void askQuestion_whenRetrievalEmpty_returnsInsufficientContextNotice() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createSinglePagePdf(
                "Baking recipes: mix flour, sugar, and butter at 350 degrees."
        );

        // Upload and Index
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "recipes.pdf", "application/pdf", pdfBytes)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Ask question with extremely high similarity threshold that yields 0 chunks
        String requestJson = """
                {
                    "question": "Quantum mechanics and wave-particle duality",
                    "topK": 2,
                    "minThreshold": 0.99
                }
                """;

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSufficientContext").value(false))
                .andExpect(jsonPath("$.answer").value(containsString("does not contain relevant information")))
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations", hasSize(0)));
    }

    @Test
    @DisplayName("Re-indexing a new document updates citations and query answers to new document")
    void askQuestion_afterDocumentReplacement_citesNewDocument() throws Exception {
        // Document 1: Physics
        byte[] doc1Bytes = PdfTestUtils.createSinglePagePdf("Thermodynamics is the branch of physics that deals with heat.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "physics.pdf", "application/pdf", doc1Bytes)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Document 2: History (replaces Document 1)
        byte[] doc2Bytes = PdfTestUtils.createSinglePagePdf("The Renaissance was a fervent period of cultural, artistic, and economic rebirth.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "history.pdf", "application/pdf", doc2Bytes)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Query
        String requestJson = """
                {
                    "question": "What was the Renaissance?"
                }
                """;

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations[0].sourceFilename").value("history.pdf"))
                .andExpect(jsonPath("$.answer").value(containsString("Page 1")));
    }
}
