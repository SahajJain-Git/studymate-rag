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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 * - Evidence Sufficiency Heuristic:
 *   To verify the RAG workflow and avoid claiming ungrounded factual confidence, this baseline
 *   evaluates lexical keyword overlap between the student's question and retrieved context passages
 *   (ignoring common stop words, punctuation, and generic question templates).
 *   If no meaningful lexical overlap exists, it adheres to Rule 2 of the study assistant system prompt
 *   and explicitly reports insufficient information.
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
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[a-zA-Z0-9]{3,}\\b");

    private static final String INSUFFICIENT_INFO_RESPONSE =
            "The provided document does not contain sufficient information to answer this question.";

    // Common English stop words and generic query template words that do NOT constitute domain evidence
    private static final Set<String> STOP_WORDS = Set.of(
            "about", "above", "after", "again", "against", "all", "and", "any", "are",
            "because", "been", "before", "being", "below", "between", "both", "but",
            "can", "could", "did", "does", "doing", "down", "during", "each", "explain",
            "few", "for", "from", "further", "had", "has", "have", "having", "her", "here",
            "hers", "herself", "him", "himself", "his", "how", "into", "its", "itself",
            "just", "more", "most", "myself", "nor", "not", "now", "off", "once", "only",
            "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "please",
            "same", "she", "should", "some", "such", "tell", "than", "that", "the", "their",
            "theirs", "them", "themselves", "then", "there", "these", "they", "this", "those",
            "through", "too", "under", "until", "very", "was", "were", "what", "when", "where",
            "which", "while", "who", "whom", "why", "will", "with", "would", "you", "your",
            "yours", "yourself", "yourselves", "document", "passage", "text", "study", "page",
            "chapter", "notes", "information", "detail", "details", "definition", "define"
    );

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

        // 1. Guardrail: Must contain context passages marker
        if (!promptContent.contains("--- Context Passages ---") ||
            promptContent.contains("No relevant context passages were found")) {
            return buildResponse(INSUFFICIENT_INFO_RESPONSE);
        }

        // 2. Extract context section and student question
        int contextStart = promptContent.indexOf("--- Context Passages ---");
        int contextEnd = promptContent.indexOf("--- End Context Passages ---");
        if (contextStart == -1 || contextEnd == -1 || contextEnd <= contextStart) {
            return buildResponse(INSUFFICIENT_INFO_RESPONSE);
        }

        String contextSection = promptContent.substring(
                contextStart + "--- Context Passages ---".length(), contextEnd).trim();

        String question = extractQuestion(promptContent);

        // 3. Extract meaningful content keywords from question and context
        Set<String> questionTokens = extractContentTokens(question);
        Set<String> contextTokens = extractContentTokens(contextSection);

        // If the question has no domain content tokens (only stop words like "What is it?"),
        // or if there is zero lexical evidence overlap with retrieved passages,
        // strictly return the insufficient information response
        if (questionTokens.isEmpty() || !hasLexicalOverlap(questionTokens, contextTokens)) {
            log.info("Insufficient lexical evidence found between question '{}' and context passages. Returning insufficient context.",
                    question);
            return buildResponse(INSUFFICIENT_INFO_RESPONSE);
        }

        // 4. Find cited page numbers from the prompt context blocks
        Matcher matcher = PAGE_MARKER_PATTERN.matcher(contextSection);
        StringBuilder citationsList = new StringBuilder();
        while (matcher.find()) {
            String pageCitation = "[Page " + matcher.group(1) + "]";
            if (!citationsList.toString().contains(pageCitation)) {
                if (!citationsList.isEmpty()) {
                    citationsList.append(", ");
                }
                citationsList.append(pageCitation);
            }
        }

        String citedPages = citationsList.isEmpty() ? "[Page 1]" : citationsList.toString();

        // 5. Find the most relevant context passage containing the matching question tokens
        String bestSnippet = findBestSnippet(contextSection, questionTokens);

        String answer;
        if (!bestSnippet.isEmpty()) {
            answer = String.format("Based on %s, %s", citedPages, bestSnippet);
        } else {
            answer = String.format("Based on the provided context in %s, the document discusses this topic.", citedPages);
        }

        return buildResponse(answer);
    }

    private String extractQuestion(String promptContent) {
        int questionIndex = promptContent.lastIndexOf("Student Question:");
        if (questionIndex != -1) {
            return promptContent.substring(questionIndex + "Student Question:".length()).trim();
        }
        return promptContent;
    }

    private Set<String> extractContentTokens(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        Matcher m = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String word = m.group();
            if (!STOP_WORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private boolean hasLexicalOverlap(Set<String> questionTokens, Set<String> contextTokens) {
        for (String q : questionTokens) {
            for (String c : contextTokens) {
                if (isStemMatch(q, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isStemMatch(String w1, String w2) {
        if (w1.equals(w2)) {
            return true;
        }
        if (w1.length() < 4 || w2.length() < 4) {
            return false;
        }
        if (w1.startsWith(w2) || w2.startsWith(w1)) {
            return true;
        }
        // Practical stemming: check shared prefix (e.g. "encapsulate" and "encapsulation" share "encapsulat")
        int minLen = Math.min(w1.length(), w2.length());
        int commonPrefix = 0;
        for (int i = 0; i < minLen; i++) {
            if (w1.charAt(i) == w2.charAt(i)) {
                commonPrefix++;
            } else {
                break;
            }
        }
        return commonPrefix >= 5 && commonPrefix >= (minLen - 2);
    }


    private String findBestSnippet(String contextSection, Set<String> questionTokens) {
        String[] lines = contextSection.split("\n");
        String fallbackLine = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("---") || trimmed.startsWith("[Page") || trimmed.startsWith("Source:")) {
                continue;
            }

            if (fallbackLine.isEmpty()) {
                fallbackLine = trimmed;
            }

            // Check if this line contains any matching question token directly or via stem
            String lower = trimmed.toLowerCase(Locale.ROOT);
            boolean matched = false;
            for (String token : questionTokens) {
                if (lower.contains(token)) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                Matcher lineMatcher = WORD_PATTERN.matcher(lower);
                while (lineMatcher.find()) {
                    String lineWord = lineMatcher.group();
                    for (String token : questionTokens) {
                        if (isStemMatch(token, lineWord)) {
                            matched = true;
                            break;
                        }
                    }
                    if (matched) break;
                }
            }

            if (matched) {
                if (trimmed.length() > 160) {
                    return trimmed.substring(0, 160).trim() + "...";
                }
                return trimmed;
            }
        }

        if (fallbackLine.length() > 160) {
            return fallbackLine.substring(0, 160).trim() + "...";
        }
        return fallbackLine;
    }


    private ChatResponse buildResponse(String text) {
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(generation));
    }
}
