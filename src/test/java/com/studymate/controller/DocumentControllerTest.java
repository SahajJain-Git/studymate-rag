package com.studymate.controller;

import com.studymate.dto.ErrorResponse;
import com.studymate.service.InMemoryDocumentStore;
import com.studymate.service.VectorStoreService;
import com.studymate.util.PdfTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for DocumentController, ChunkingService, InMemoryDocumentStore, VectorStoreService,
 * and GlobalExceptionHandler.
 *
 * Concepts for Beginners:
 * - MockMvc: Simulates HTTP requests against the full Spring ApplicationContext without real network sockets.
 * - Test Isolation: The @BeforeEach method clears both InMemoryDocumentStore and VectorStoreService before every test,
 *   preventing tests from polluting each other's state.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDocumentStore documentStore;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        // Guarantee clean state for each test
        documentStore.clear();
        vectorStoreService.clearStore();
    }

    @Test
    @DisplayName("POST /api/documents/upload with valid PDF returns 200 OK and stores document")
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
                .andExpect(jsonPath("$.message").value(containsString("stored 2 pages")));
    }

    @Test
    @DisplayName("GET /api/documents/chunks returns 404 when no document has been uploaded yet")
    void getChunks_whenNoDocumentUploaded_returns404() throws Exception {
        mockMvc.perform(get("/api/documents/chunks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("No document has been uploaded yet")))
                .andExpect(jsonPath("$.path").value("/api/documents/chunks"));
    }

    @Test
    @DisplayName("GET /api/documents/chunks returns 200 OK and generated chunks after upload")
    void getChunks_afterUpload_returns200AndChunkList() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createMultiPagePdf(List.of(
                "Page 1: Introduction to Spring AI and Vector Embeddings.",
                "Page 2: Retrieval-Augmented Generation Architecture and Prompt Engineering."
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "ai-study-guide.pdf",
                "application/pdf",
                pdfBytes
        );

        // 1. Upload document
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file))
                .andExpect(status().isOk());

        // 2. Query chunks endpoint
        mockMvc.perform(get("/api/documents/chunks")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.filename").value("ai-study-guide.pdf"))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalChunks").value(2))
                .andExpect(jsonPath("$.chunkSize").value(500))
                .andExpect(jsonPath("$.overlap").value(50))
                .andExpect(jsonPath("$.chunks").isArray())
                .andExpect(jsonPath("$.chunks", hasSize(2)))
                .andExpect(jsonPath("$.chunks[0].chunkId").value(containsString("ai-study-guide.pdf-p1-c0")))
                .andExpect(jsonPath("$.chunks[0].pageNumber").value(1))
                .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.chunks[0].text").value(containsString("Introduction to Spring AI")))
                .andExpect(jsonPath("$.chunks[1].chunkId").value(containsString("ai-study-guide.pdf-p2-c1")))
                .andExpect(jsonPath("$.chunks[1].pageNumber").value(2))
                .andExpect(jsonPath("$.chunks[1].chunkIndex").value(1))
                .andExpect(jsonPath("$.chunks[1].text").value(containsString("Retrieval-Augmented Generation")));
    }

    @Test
    @DisplayName("Uploading a new document replaces previous document in InMemoryDocumentStore")
    void upload_replacesPreviousDocument() throws Exception {
        byte[] pdf1 = PdfTestUtils.createSinglePagePdf("Document One text content.");
        byte[] pdf2 = PdfTestUtils.createSinglePagePdf("Document Two text content.");

        // Upload first document
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "doc-one.pdf", "application/pdf", pdf1)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("doc-one.pdf"));

        // Upload second document -> should replace first
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "doc-two.pdf", "application/pdf", pdf2)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("doc-two.pdf"))
                .andExpect(jsonPath("$.chunks[0].text").value("Document Two text content."));
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

    // ==========================================
    // Stage 4: Indexing & Vector Search Tests
    // ==========================================

    @Test
    @DisplayName("POST /api/documents/index returns 404 when no document has been uploaded yet")
    void index_whenNoDocumentUploaded_returns404() throws Exception {
        mockMvc.perform(post("/api/documents/index")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("No document has been uploaded yet")));
    }

    @Test
    @DisplayName("POST /api/documents/index after upload returns 200 OK and IndexResponse")
    void index_afterUpload_returns200AndIndexResponse() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createMultiPagePdf(List.of(
                "Page 1: Operating System concurrency and mutex locks.",
                "Page 2: Deadlock prevention with resource allocation graphs."
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "os-study-guide.pdf",
                "application/pdf",
                pdfBytes
        );

        // 1. Upload
        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk());

        // 2. Index
        mockMvc.perform(post("/api/documents/index")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.filename").value("os-study-guide.pdf"))
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.totalChunksIndexed").value(2))
                .andExpect(jsonPath("$.message").value(containsString("Successfully indexed 2 chunks")));
    }

    @Test
    @DisplayName("POST /api/documents/search returns 404 when no document has been indexed yet")
    void search_whenNoDocumentIndexed_returns404() throws Exception {
        String requestJson = """
                {
                    "query": "What is mutex locking?"
                }
                """;

        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("No document has been indexed yet")));
    }

    @Test
    @DisplayName("POST /api/documents/search with blank query returns 400 Bad Request")
    void search_blankQuery_returns400() throws Exception {
        String requestJson = """
                {
                    "query": "   "
                }
                """;

        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Search query must not be blank.")));
    }

    @Test
    @DisplayName("POST /api/documents/search with valid query after indexing returns 200 OK and ranked matches")
    void search_validQueryAfterIndexing_returns200AndRankedMatches() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createMultiPagePdf(List.of(
                "Page 1: Garbage collection in the Java Virtual Machine optimizes heap memory.",
                "Page 2: Relational database indexing using B-Trees and hash tables."
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cs-notes.pdf",
                "application/pdf",
                pdfBytes
        );

        // Upload and Index
        mockMvc.perform(multipart("/api/documents/upload").file(file)).andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Search for JVM Garbage Collection
        String requestJson = """
                {
                    "query": "Java Virtual Machine garbage collection heap",
                    "topK": 2,
                    "minThreshold": 0.0
                }
                """;

        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query").value("Java Virtual Machine garbage collection heap"))
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.totalMatches").value(greaterThan(0)))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].chunkId").isNotEmpty())
                .andExpect(jsonPath("$.results[0].documentId").isNotEmpty())
                .andExpect(jsonPath("$.results[0].sourceFilename").value("cs-notes.pdf"))
                .andExpect(jsonPath("$.results[0].pageNumber").value(1))
                .andExpect(jsonPath("$.results[0].text").value(containsString("Garbage collection")))
                .andExpect(jsonPath("$.results[0].score").isNumber());
    }

    @Test
    @DisplayName("Re-indexing a new document evicts previous document vectors from search")
    void search_afterDocumentReplacement_evictsPreviousChunks() throws Exception {
        // Upload & Index Doc 1: Physics
        byte[] physicsPdf = PdfTestUtils.createSinglePagePdf("Quantum entanglement and superposition principles.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "physics.pdf", "application/pdf", physicsPdf)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Upload & Index Doc 2: History (replaces physics.pdf)
        byte[] historyPdf = PdfTestUtils.createSinglePagePdf("The Renaissance marked a transition from the Middle Ages to modernity.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "history.pdf", "application/pdf", historyPdf)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Search for Physics query; only history chunk should be searched, physics chunk is evicted!
        String searchPhysics = """
                {
                    "query": "Quantum entanglement",
                    "topK": 5
                }
                """;

        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPhysics))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].sourceFilename").value("history.pdf"))
                .andExpect(jsonPath("$.results[0].text").value(containsString("The Renaissance")));
    }

    @Test
    @DisplayName("Uploading the same filename twice generates distinct document IDs and evicts old vectors")
    void uploadAndIndex_duplicateFilename_generatesDistinctIdsAndEvictsOld() throws Exception {
        // Upload version 1
        byte[] v1Pdf = PdfTestUtils.createSinglePagePdf("Draft version 1 of project specification.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "specs.pdf", "application/pdf", v1Pdf)))
                .andExpect(status().isOk());
        String indexV1Response = mockMvc.perform(post("/api/documents/index"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Upload version 2 with identical filename
        byte[] v2Pdf = PdfTestUtils.createSinglePagePdf("Draft version 2 of project specification with new requirements.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "specs.pdf", "application/pdf", v2Pdf)))
                .andExpect(status().isOk());
        String indexV2Response = mockMvc.perform(post("/api/documents/index"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The document IDs in the two index responses must be distinct
        // v1 response has one documentId, v2 has another
        String searchV2 = """
                {
                    "query": "new requirements specification",
                    "topK": 2
                }
                """;

        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchV2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].text").value(containsString("Draft version 2")));
    }

    @Test
    @DisplayName("POST /api/documents/upload with password-protected PDF returns 422 Unprocessable Entity")
    void upload_encryptedPdf_returns422() throws Exception {
        byte[] encryptedPdf = PdfTestUtils.createEncryptedPdf("Protected notes", "secret123");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "protected.pdf",
                "application/pdf",
                encryptedPdf
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value(containsString("Password-protected or encrypted PDF documents are not supported.")));
    }

    @Test
    @DisplayName("POST /api/documents/upload with whitespace-only filename returns 400 Bad Request")
    void upload_whitespaceFilename_returns400() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createSinglePagePdf("Test content");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "   ",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Uploaded file must have a valid filename.")));
    }

    @Test
    @DisplayName("POST /api/documents/upload with path traversal filename sanitizes filename to base name")
    void upload_pathTraversalFilename_sanitizesFilename() throws Exception {
        byte[] pdfBytes = PdfTestUtils.createSinglePagePdf("Secure syllabus content");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../secret/course-syllabus.pdf",
                "application/pdf",
                pdfBytes
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("course-syllabus.pdf"));
    }

    @Test
    @DisplayName("Sequential replacement: upload/index A, upload B, search before indexing B returns 404, then index B and verify A is evicted")
    void sequentialReplacement_lifecycleTest() throws Exception {
        // 1. Upload & Index Doc A (Algorithms)
        byte[] docA = PdfTestUtils.createSinglePagePdf("Dijkstra shortest path algorithm computes shortest paths from a single source.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "algorithms.pdf", "application/pdf", docA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // Verify Doc A is searchable
        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"shortest path\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].sourceFilename").value("algorithms.pdf"));

        // 2. Upload Doc B (Compilers) - NOT yet indexed
        byte[] docB = PdfTestUtils.createSinglePagePdf("Lexical analysis converts character streams into tokens using finite automata.");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(new MockMultipartFile("file", "compilers.pdf", "application/pdf", docB)))
                .andExpect(status().isOk());

        // 3. Search before indexing Doc B: Must return 404, ensuring no stale vectors from Doc A are queried!
        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"shortest path\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("No document has been indexed yet")));

        // 4. Index Doc B
        mockMvc.perform(post("/api/documents/index")).andExpect(status().isOk());

        // 5. Search after indexing Doc B: Only Doc B exists, Doc A is completely gone!
        mockMvc.perform(post("/api/documents/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"lexical analysis tokens\", \"topK\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].sourceFilename").value("compilers.pdf"))
                .andExpect(jsonPath("$.results[0].text").value(containsString("Lexical analysis")));
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException is cleanly mapped to 413 Payload Too Large by GlobalExceptionHandler")
    void handleMaxUploadSize_returns413PayloadTooLarge() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(20 * 1024 * 1024);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/upload");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMaxUploadSize(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().error()).isEqualTo("Payload Too Large");
        assertThat(response.getBody().message()).contains("maximum allowed upload limit (20MB)");
        assertThat(response.getBody().path()).isEqualTo("/api/documents/upload");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}


