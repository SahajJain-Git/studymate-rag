package com.studymate.service;

import com.studymate.dto.CitationItem;
import com.studymate.dto.QuestionRequest;
import com.studymate.dto.QuestionResponse;
import com.studymate.dto.SearchResultItem;
import com.studymate.exception.NoDocumentIndexedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating Retrieval-Augmented Generation (RAG) question answering.
 *
 * Concepts for Beginners:
 * - What is RAG?
 *   Instead of asking an LLM to rely on memorized internet training data (which hallucinates
 *   or knows nothing about your specific class syllabus), RAG first retrieves the most relevant
 *   passages from your uploaded document, injects them into an augmented prompt, and instructs
 *   the LLM to answer strictly from that retrieved context.
 * - Citation Integrity:
 *   Citations are generated deterministically in Java from the actual vector search results
 *   returned by VectorStoreService, never from ungrounded model output.
 * - Insufficient Evidence Guardrail:
 *   If vector retrieval returns 0 chunks, we short-circuit immediately without calling the LLM,
 *   saving latency and avoiding hallucinations. Furthermore, even if chunks are retrieved, the
 *   prompt explicitly warns the LLM that retrieved passages might not contain sufficient evidence.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String SYSTEM_PROMPT = """
            You are StudyMate, an expert, patient, and strict AI study assistant.
            Your task is to answer the student's question accurately, clearly, and concisely using ONLY the provided Context Passages below.

            Strict Grounding Rules:
            1. Rely exclusively on the facts explicitly stated in the Context Passages. Do NOT speculate, extrapolate, or introduce outside knowledge.
            2. If the Context Passages do not contain sufficient evidence to answer the question directly, explicitly respond with:
               "The provided document does not contain sufficient information to answer this question."
            3. Note: The presence of retrieved context passages does NOT guarantee they contain sufficient evidence to answer the question.
            4. Whenever you state a fact or definition, cite the source page using the bracket notation, e.g., [Page 1].
            """;

    private static final String NO_CONTEXT_ANSWER =
            "The uploaded study document does not contain relevant information to answer this question.";

    private final VectorStoreService vectorStoreService;
    private final ChatModel chatModel;
    private final int defaultTopK;

    public RagService(
            VectorStoreService vectorStoreService,
            ChatModel chatModel,
            @Value("${studymate.retrieval.top-k:4}") int defaultTopK) {
        this.vectorStoreService = vectorStoreService;
        this.chatModel = chatModel;
        this.defaultTopK = defaultTopK;
    }

    /**
     * Answers a student question by retrieving relevant document passages and synthesizing a grounded response.
     *
     * @param request The question request containing question text and retrieval parameters
     * @return QuestionResponse containing the answer, authoritative citations, and context sufficiency flag
     * @throws NoDocumentIndexedException if no document has been indexed in the vector store (HTTP 404)
     */
    public QuestionResponse answerQuestion(QuestionRequest request) {
        if (request == null || request.question() == null || request.question().trim().isEmpty()) {
            throw new IllegalArgumentException("Question must not be blank.");
        }

        // 1. Verify that a document is currently indexed in the vector store
        if (!vectorStoreService.hasIndexedDocument()) {
            throw new NoDocumentIndexedException(
                    "No document has been indexed yet. Please upload and index a document first via POST /api/documents/index.");
        }

        String documentId = vectorStoreService.getIndexedDocumentId();
        String question = request.question().trim();
        int topK = (request.topK() != null && request.topK() > 0) ? request.topK() : defaultTopK;
        double minThreshold = (request.minThreshold() != null && request.minThreshold() >= 0.0)
                ? request.minThreshold()
                : 0.0;

        log.info("Executing RAG question answering for question: '{}' (topK={}, minThreshold={})",
                question, topK, minThreshold);

        // 2. Retrieve relevant chunks using VectorStoreService
        List<SearchResultItem> retrievedChunks = vectorStoreService.similaritySearch(question, topK, minThreshold);

        // 3. Handle empty retrieval before calling ChatModel (insufficient context short-circuit)
        if (retrievedChunks.isEmpty()) {
            log.info("Retrieval returned 0 chunks for question: '{}'. Returning insufficient context response without calling LLM.",
                    question);
            return new QuestionResponse(
                    question,
                    NO_CONTEXT_ANSWER,
                    List.of(),
                    documentId,
                    false
            );
        }

        // 4. Build authoritative citations directly in Java from retrieved chunk metadata
        List<CitationItem> citations = buildCitations(retrievedChunks);

        // 5. Synthesize augmented prompt for the LLM
        String contextPassages = formatContextPassages(retrievedChunks);
        String userContent = String.format("%s\n\nStudent Question: %s", contextPassages, question);

        Message systemMessage = new SystemMessage(SYSTEM_PROMPT);
        Message userMessage = new UserMessage(userContent);
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        log.debug("Calling ChatModel with {} context passages for question: '{}'", retrievedChunks.size(), question);

        // 6. Invoke ChatModel
        ChatResponse chatResponse = chatModel.call(prompt);
        String rawAnswer = extractAnswer(chatResponse);

        // 7. Check if the model or retrieved content indicates insufficient evidence
        boolean hasSufficientContext = evaluateContextSufficiency(rawAnswer);

        log.info("Successfully generated RAG response for question: '{}' (hasSufficientContext={})",
                question, hasSufficientContext);

        return new QuestionResponse(
                question,
                rawAnswer,
                citations,
                documentId,
                hasSufficientContext
        );
    }

    private List<CitationItem> buildCitations(List<SearchResultItem> chunks) {
        List<CitationItem> citations = new ArrayList<>(chunks.size());
        for (SearchResultItem chunk : chunks) {
            String fullText = chunk.text().trim();
            String preview = fullText.length() > 140
                    ? fullText.substring(0, 140).trim() + "..."
                    : fullText;

            citations.add(new CitationItem(
                    chunk.pageNumber(),
                    chunk.sourceFilename(),
                    chunk.chunkId(),
                    chunk.score(),
                    preview
            ));
        }
        return citations;
    }

    private String formatContextPassages(List<SearchResultItem> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- Context Passages ---\n");
        for (SearchResultItem chunk : chunks) {
            sb.append(String.format("[Page %d] (Source: %s, Chunk: %s):\n%s\n\n",
                    chunk.pageNumber(),
                    chunk.sourceFilename(),
                    chunk.chunkId(),
                    chunk.text().trim()));
        }
        sb.append("--- End Context Passages ---");
        return sb.toString();
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return NO_CONTEXT_ANSWER;
        }
        String content = response.getResult().getOutput().getText();
        return (content != null && !content.isBlank()) ? content.trim() : NO_CONTEXT_ANSWER;
    }

    private boolean evaluateContextSufficiency(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String lower = answer.toLowerCase();
        return !lower.contains("does not contain sufficient information") &&
               !lower.contains("does not contain enough information") &&
               !lower.contains("insufficient information") &&
               !lower.contains("unable to answer");
    }
}
