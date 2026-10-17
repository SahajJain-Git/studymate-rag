package com.studymate.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic local testing baseline implementing Spring AI's ChatModel interface.
 *
 * IMPORTANT TRANSPARENCY NOTICE:
 * - This class is an in-process, deterministic baseline designed strictly for local development,
 *   offline automated testing, and CI/CD validation without requiring paid API credentials
 *   or external network connections.
 * - It is NOT a generative neural large language model (such as OpenAI GPT-4o, Anthropic Claude,
 *   or Meta Llama). It DOES NOT perform genuine linguistic comprehension or creative synthesis.
 * - Why this exists:
 *   It allows testing the entire RAG pipeline end-to-end (prompt augmentation, context insertion,
 *   guardrail verification, and citation linking) in pure Java with 100% determinism.
 * - When an actual external model is configured (such as OpenAI or Ollama), this bean can be
 *   seamlessly substituted because it implements the standard Spring AI ChatModel interface.
 */
@Component
public class DeterministicLocalChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(DeterministicLocalChatModel.class);
    private static final Pattern PAGE_MARKER_PATTERN = Pattern.compile("\\[Page\\s+(\\d+)\\]");

    public DeterministicLocalChatModel() {
        log.info("Initialized DeterministicLocalChatModel (offline test baseline). " +
                 "No external LLM credentials or network calls required.");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
            return buildResponse("The provided prompt was empty. Unable to generate an answer.");
        }

        StringBuilder fullPromptText = new StringBuilder();
        for (Message message : prompt.getInstructions()) {
            if (message != null && message.getText() != null) {
                fullPromptText.append(message.getText()).append("\n");
            }
        }

        String promptContent = fullPromptText.toString().trim();
        log.debug("DeterministicLocalChatModel received prompt with {} characters.", promptContent.length());

        // Check if the prompt contains context passages
        if (!promptContent.contains("--- Context Passages ---") ||
            promptContent.contains("No relevant context passages were found")) {
            return buildResponse("The provided document does not contain sufficient information to answer this question.");
        }

        // Find cited page numbers from the prompt context blocks
        Matcher matcher = PAGE_MARKER_PATTERN.matcher(promptContent);
        StringBuilder citationsList = new StringBuilder();
        while (matcher.find()) {
            if (!citationsList.isEmpty()) {
                citationsList.append(", ");
            }
            citationsList.append("[Page ").append(matcher.group(1)).append("]");
        }

        String citedPages = citationsList.isEmpty() ? "[Page 1]" : citationsList.toString();

        // Extract context section for a grounded response summary
        int contextStart = promptContent.indexOf("--- Context Passages ---");
        int contextEnd = promptContent.indexOf("--- End Context Passages ---");
        String contextSnippet = "";
        if (contextStart != -1 && contextEnd != -1 && contextEnd > contextStart) {
            String rawContext = promptContent.substring(contextStart, contextEnd);
            // Grab the first line of actual content after the page marker
            String[] lines = rawContext.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("---") && !trimmed.startsWith("[Page") && !trimmed.startsWith("Source:")) {
                    contextSnippet = trimmed;
                    if (contextSnippet.length() > 160) {
                        contextSnippet = contextSnippet.substring(0, 160) + "...";
                    }
                    break;
                }
            }
        }

        String answer;
        if (!contextSnippet.isEmpty()) {
            answer = String.format("Based on %s, %s", citedPages, contextSnippet);
        } else {
            answer = String.format("Based on the provided context in %s, the document discusses this topic.", citedPages);
        }

        return buildResponse(answer);
    }

    private ChatResponse buildResponse(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(generation));
    }
}
