package com.studymate.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for DeterministicLocalChatModel.
 *
 * Verifies the deterministic lexical evidence heuristic:
 * - Relevant questions retrieve grounded snippets and citations.
 * - Unrelated questions trigger the strict insufficient evidence notice.
 * - Common-word-only questions trigger insufficient evidence notice.
 * - Empty context passages trigger insufficient evidence notice.
 * - Practical wording variations (stemming/prefixes) match appropriately.
 */
@DisplayName("DeterministicLocalChatModel Unit Tests")
class DeterministicLocalChatModelTest {

    private DeterministicLocalChatModel chatModel;

    private static final String OOP_PROMPT_TEMPLATE = """
            You are StudyMate, a helpful study assistant.

            --- Context Passages ---
            [Page 3] (Source: oop.pdf, Chunk: c1):
            Encapsulation hides the internal representation of an object.

            [Page 4] (Source: oop.pdf, Chunk: c2):
            Polymorphism allows objects of different classes to respond to the same method call.
            --- End Context Passages ---

            Student Question: %s
            """;

    @BeforeEach
    void setUp() {
        chatModel = new DeterministicLocalChatModel();
    }

    @Test
    @DisplayName("Should return graceful response for empty or null prompt")
    void shouldHandleEmptyPrompt() {
        ChatResponse respNull = chatModel.call((Prompt) null);
        assertNotNull(respNull);
        assertTrue(respNull.getResult().getOutput().getText().contains("empty"));

        ChatResponse respEmpty = chatModel.call(new Prompt(List.of()));
        assertNotNull(respEmpty);
        assertTrue(respEmpty.getResult().getOutput().getText().contains("empty"));
    }

    @Test
    @DisplayName("Should return insufficient context message when prompt has no context passages")
    void shouldReturnInsufficientContextWhenNoContextPassages() {
        Prompt prompt = new Prompt(List.of(new UserMessage("What is the capital of France?")));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("does not contain sufficient information"));
    }

    @Test
    @DisplayName("Should synthesize citation-grounded response when relevant question matches context")
    void shouldSynthesizeCitationGroundedResponse() {
        String promptContent = String.format(OOP_PROMPT_TEMPLATE, "What is encapsulation?");
        Prompt prompt = new Prompt(List.of(new UserMessage(promptContent)));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("[Page 3]"));
        assertTrue(text.contains("Encapsulation hides"));
    }

    @Test
    @DisplayName("Should target specific passage when second concept (polymorphism) is queried")
    void shouldTargetSecondPassageForPolymorphismQuery() {
        String promptContent = String.format(OOP_PROMPT_TEMPLATE, "Explain polymorphism in programming?");
        Prompt prompt = new Prompt(List.of(new UserMessage(promptContent)));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("[Page 4]"));
        assertTrue(text.contains("Polymorphism allows"));
    }

    @Test
    @DisplayName("Should return insufficient information when question is completely unrelated to context")
    void shouldReturnInsufficientInformationForUnrelatedQuestion() {
        String promptContent = String.format(OOP_PROMPT_TEMPLATE, "How do you bake a chocolate cake at 350 degrees?");
        Prompt prompt = new Prompt(List.of(new UserMessage(promptContent)));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("does not contain sufficient information to answer this question"));
        assertFalse(text.contains("Encapsulation"));
        assertFalse(text.contains("Polymorphism"));
    }

    @Test
    @DisplayName("Should return insufficient information when question contains only common stop words")
    void shouldReturnInsufficientInformationForCommonWordsOnlyQuestion() {
        String promptContent = String.format(OOP_PROMPT_TEMPLATE, "What is this and how does it do that?");
        Prompt prompt = new Prompt(List.of(new UserMessage(promptContent)));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("does not contain sufficient information to answer this question"));
    }

    @Test
    @DisplayName("Should return insufficient information when context passages section is blank")
    void shouldReturnInsufficientInformationForBlankContext() {
        String promptContent = """
                You are StudyMate.

                --- Context Passages ---
                --- End Context Passages ---

                Student Question: What is encapsulation?
                """;
        Prompt prompt = new Prompt(List.of(new UserMessage(promptContent)));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("does not contain sufficient information"));
    }

    @Test
    @DisplayName("Should match practical wording variations through stemming and prefix matching")
    void shouldMatchWordingVariations() {
        // "encapsulate" matches "encapsulation"
        String promptContent = String.format(OOP_PROMPT_TEMPLATE, "Why should we encapsulate fields?");
        Prompt prompt = new Prompt(List.of(new UserMessage(promptContent)));
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        String text = response.getResult().getOutput().getText();
        assertTrue(text.contains("[Page 3]"));
        assertTrue(text.contains("Encapsulation"));
    }

    @Test
    @DisplayName("Should support call(String) convenience method")
    void shouldSupportCallString() {
        String answer = chatModel.call("Just a plain text prompt without context");
        assertNotNull(answer);
        assertFalse(answer.isBlank());
        assertTrue(answer.contains("does not contain sufficient information"));
    }
}
