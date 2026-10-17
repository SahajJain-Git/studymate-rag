package com.studymate.service;

import com.studymate.ai.DeterministicLocalEmbeddingModel;
import com.studymate.dto.CitationItem;
import com.studymate.dto.QuestionRequest;
import com.studymate.dto.QuestionResponse;
import com.studymate.exception.NoDocumentIndexedException;
import com.studymate.model.ExtractedDocument;
import com.studymate.model.ExtractedPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RagService Unit Tests")
class RagServiceTest {

    private VectorStoreService vectorStoreService;
    private RecordingChatModel recordingChatModel;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        DeterministicLocalEmbeddingModel embeddingModel = new DeterministicLocalEmbeddingModel();
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        ChunkingService chunkingService = new ChunkingService(250, 25);
        vectorStoreService = new VectorStoreService(vectorStore, chunkingService);

        recordingChatModel = new RecordingChatModel();
        ragService = new RagService(vectorStoreService, recordingChatModel, 3);
    }

    @Test
    @DisplayName("Should throw NoDocumentIndexedException when no document is indexed")
    void shouldThrowExceptionWhenNoDocumentIndexed() {
        QuestionRequest request = new QuestionRequest("What is virtual memory?");
        assertThrows(NoDocumentIndexedException.class, () -> ragService.answerQuestion(request));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when question is blank")
    void shouldThrowExceptionWhenQuestionIsBlank() {
        // Index a dummy document first
        ExtractedDocument doc = new ExtractedDocument(
                "doc-1",
                "sample.pdf",
                1,
                100,
                List.of(new ExtractedPage(1, "Sample content for testing.", 27))
        );
        vectorStoreService.indexDocument(doc);

        assertThrows(IllegalArgumentException.class, () ->
                ragService.answerQuestion(new QuestionRequest("   "))
        );
    }

    @Test
    @DisplayName("Should short-circuit without calling ChatModel when retrieval returns 0 chunks")
    void shouldShortCircuitWhenRetrievalReturnsNoChunks() {
        ExtractedDocument doc = new ExtractedDocument(
                "doc-1",
                "cooking.pdf",
                1,
                100,
                List.of(new ExtractedPage(1, "Baking chocolate cookies and cakes in an oven.", 46))
        );
        vectorStoreService.indexDocument(doc);

        // Query with a high threshold that filters out all chunks
        QuestionRequest request = new QuestionRequest("Quantum entanglement in physics", 2, 0.99);
        QuestionResponse response = ragService.answerQuestion(request);

        assertNotNull(response);
        assertFalse(response.hasSufficientContext());
        assertTrue(response.citations().isEmpty());
        assertTrue(response.answer().contains("does not contain relevant information"));

        // Crucial verification: ChatModel must NOT have been called!
        assertEquals(0, recordingChatModel.callCount, "ChatModel must not be called when retrieval is empty");
    }

    @Test
    @DisplayName("Should synthesize prompt, call ChatModel, and construct authoritative citations")
    void shouldAnswerQuestionWithAuthoritativeCitations() {
        ExtractedDocument doc = new ExtractedDocument(
                "doc-os",
                "operating_systems.pdf",
                2,
                300,
                List.of(
                        new ExtractedPage(1, "Virtual memory provides an abstraction of contiguous address spaces for processes.", 82),
                        new ExtractedPage(2, "Deadlock avoidance algorithms like the Banker algorithm manage allocation states.", 83)
                )
        );
        vectorStoreService.indexDocument(doc);

        QuestionRequest request = new QuestionRequest("What is virtual memory address spaces?", 2, 0.0);
        QuestionResponse response = ragService.answerQuestion(request);

        assertNotNull(response);
        assertEquals("What is virtual memory address spaces?", response.question());
        assertEquals("doc-os", response.documentId());
        assertTrue(response.hasSufficientContext());
        assertFalse(response.citations().isEmpty());

        // Verify citations constructed directly in Java
        CitationItem topCitation = response.citations().get(0);
        assertEquals(1, topCitation.pageNumber());
        assertEquals("operating_systems.pdf", topCitation.sourceFilename());
        assertTrue(topCitation.chunkId().startsWith("doc-os"));
        assertTrue(topCitation.textPreview().contains("Virtual memory"));
        assertTrue(topCitation.score() > 0.0);

        // Verify ChatModel was called once with augmented prompt
        assertEquals(1, recordingChatModel.callCount);
        assertNotNull(recordingChatModel.lastPrompt);

        // Verify the prompt content contains system instructions and context passages
        List<Message> instructions = recordingChatModel.lastPrompt.getInstructions();
        assertEquals(2, instructions.size());
        assertTrue(instructions.get(0).getText().contains("Strict Grounding Rules"));
        assertTrue(instructions.get(1).getText().contains("--- Context Passages ---"));
        assertTrue(instructions.get(1).getText().contains("[Page 1] (Source: operating_systems.pdf"));
    }

    @Test
    @DisplayName("Should mark hasSufficientContext as false if model indicates insufficient information")
    void shouldMarkInsufficientContextWhenModelCannotAnswer() {
        ExtractedDocument doc = new ExtractedDocument(
                "doc-test",
                "test.pdf",
                1,
                100,
                List.of(new ExtractedPage(1, "General introduction to science and physics.", 43))
        );
        vectorStoreService.indexDocument(doc);

        recordingChatModel.simulatedResponse =
                "The provided document does not contain sufficient information to answer this question.";

        QuestionRequest request = new QuestionRequest("What is the speed of light in vacuum?", 2, 0.0);
        QuestionResponse response = ragService.answerQuestion(request);

        assertNotNull(response);
        assertFalse(response.hasSufficientContext(), "Context sufficiency flag must be false when model cannot answer");
        assertTrue(response.answer().contains("does not contain sufficient information"));
    }

    /**
     * Pure-Java test stub that records invocations and returns simulated answers.
     */
    private static class RecordingChatModel implements ChatModel {
        int callCount = 0;
        Prompt lastPrompt;
        String simulatedResponse = "Based on [Page 1], virtual memory enables isolated address spaces.";

        @Override
        public ChatResponse call(Prompt prompt) {
            this.callCount++;
            this.lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(simulatedResponse))));
        }
    }
}
