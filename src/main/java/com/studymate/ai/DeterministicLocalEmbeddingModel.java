package com.studymate.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic local lexical testing baseline implementing Spring AI's EmbeddingModel interface.
 *
 * IMPORTANT TRANSPARENCY NOTICE:
 * - This class is an in-process, deterministic lexical vectorizer designed strictly for local
 *   development, offline testing, and CI/CD validation.
 * - It is NOT a neural transformer or semantic embedding model (such as OpenAI text-embedding-3
 *   or HuggingFace all-MiniLM). It does NOT perform true semantic reasoning or deep language understanding.
 * - How it works:
 *   1. Tokenizes text into lowercase alphanumeric words and character 3-grams.
 *   2. Uses deterministic feature hashing (SimHash-style) to project tokens across 384 fixed dimensions.
 *   3. Normalizes the resulting float vector to unit length (L2 norm = 1.0).
 * - Why this exists:
 *   It allows testing vector store storage, top-k cosine similarity math, ranking, thresholding,
 *   and citation metadata preservation without requiring paid API keys, internet connectivity,
 *   or external daemon processes (like Ollama). Texts sharing overlapping vocabulary naturally
 *   achieve positive cosine similarity.
 */
@Component
public class DeterministicLocalEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(DeterministicLocalEmbeddingModel.class);
    private static final int DEFAULT_DIMENSIONS = 384;
    private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

    private final int dimensions;

    public DeterministicLocalEmbeddingModel() {
        this(DEFAULT_DIMENSIONS);
    }

    public DeterministicLocalEmbeddingModel(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Embedding dimensions must be positive, got: " + dimensions);
        }
        this.dimensions = dimensions;
        log.info("Initialized DeterministicLocalEmbeddingModel with {} dimensions (lexical testing baseline).",
                this.dimensions);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());

        for (int i = 0; i < instructions.size(); i++) {
            float[] vector = embed(instructions.get(i));
            embeddings.add(new Embedding(vector, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            return new float[this.dimensions];
        }
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[this.dimensions];

        if (text == null || text.trim().isEmpty()) {
            return vector;
        }

        String lower = text.toLowerCase();
        var matcher = WORD_PATTERN.matcher(lower);

        // Feature hashing: words
        while (matcher.find()) {
            String word = matcher.group();
            applyTokenHash(vector, word, 1.0f);

            // Also add character 3-grams for subword matching
            if (word.length() >= 3) {
                for (int i = 0; i <= word.length() - 3; i++) {
                    String triGram = word.substring(i, i + 3);
                    applyTokenHash(vector, triGram, 0.4f);
                }
            }
        }

        // Normalize vector to unit length (L2-norm = 1.0) so dot product equals cosine similarity
        normalizeL2(vector);
        return vector;
    }

    @Override
    public int dimensions() {
        return this.dimensions;
    }

    private void applyTokenHash(float[] vector, String token, float weight) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        int hash = 1;
        for (byte b : bytes) {
            hash = 31 * hash + b;
        }

        int index = Math.abs(hash % this.dimensions);
        // Determine sign (+1 or -1) using high bit
        float sign = ((hash & 0x1000) == 0) ? 1.0f : -1.0f;
        vector[index] += sign * weight;
    }

    private void normalizeL2(float[] vector) {
        double sumSquares = 0.0;
        for (float val : vector) {
            sumSquares += (val * val);
        }

        if (sumSquares > 0.0) {
            float norm = (float) Math.sqrt(sumSquares);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
}
