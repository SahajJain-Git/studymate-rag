package com.studymate.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DeterministicLocalEmbeddingModel Unit Tests")
class DeterministicLocalEmbeddingModelTest {

    private DeterministicLocalEmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        embeddingModel = new DeterministicLocalEmbeddingModel();
    }

    @Test
    @DisplayName("Should return 384 dimensions")
    void shouldReturn384Dimensions() {
        assertEquals(384, embeddingModel.dimensions());
    }

    @Test
    @DisplayName("Should produce a 384-length float array for text input")
    void shouldProduce384LengthVector() {
        float[] vector = embeddingModel.embed("Spring Boot study assistant");
        assertNotNull(vector);
        assertEquals(384, vector.length);
    }

    @Test
    @DisplayName("Should produce normalized unit vector with L2 norm approximately 1.0")
    void shouldProduceNormalizedVector() {
        float[] vector = embeddingModel.embed("Java and Spring AI vector storage");

        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (v * v);
        }
        double norm = Math.sqrt(sumSquares);

        assertEquals(1.0, norm, 1e-4, "Vector must be normalized to unit length");
    }

    @Test
    @DisplayName("Should be deterministic: identical input generates identical vectors")
    void shouldBeDeterministicForIdenticalInput() {
        String text = "Retrieval Augmented Generation with Java 21";

        float[] vector1 = embeddingModel.embed(text);
        float[] vector2 = embeddingModel.embed(text);

        assertArrayEquals(vector1, vector2, 0.0f, "Identical inputs must produce identical embeddings");
    }

    @Test
    @DisplayName("Should produce different vectors for different text inputs")
    void shouldProduceDifferentVectorsForDifferentInputs() {
        float[] vector1 = embeddingModel.embed("Operating systems and kernel processes");
        float[] vector2 = embeddingModel.embed("Cooking recipes for Italian pizza");

        boolean identical = true;
        for (int i = 0; i < vector1.length; i++) {
            if (Math.abs(vector1[i] - vector2[i]) > 1e-6) {
                identical = false;
                break;
            }
        }

        assertFalse(identical, "Different text inputs must yield different embeddings");
    }

    @Test
    @DisplayName("Should give higher cosine similarity to semantically/lexically related texts")
    void shouldGiveHigherSimilarityToRelatedTexts() {
        String base = "Java Virtual Machine garbage collection and memory tuning";
        String related = "Java Virtual Machine heap memory allocation";
        String unrelated = "Baking chocolate cake in an oven with strawberry frosting";

        float[] vBase = embeddingModel.embed(base);
        float[] vRelated = embeddingModel.embed(related);
        float[] vUnrelated = embeddingModel.embed(unrelated);

        double simRelated = cosineSimilarity(vBase, vRelated);
        double simUnrelated = cosineSimilarity(vBase, vUnrelated);

        assertTrue(simRelated > simUnrelated,
                String.format("Expected related similarity (%.4f) > unrelated similarity (%.4f)",
                        simRelated, simUnrelated));
    }

    @Test
    @DisplayName("Should handle empty and blank text without throwing exceptions")
    void shouldHandleEmptyAndBlankText() {
        float[] emptyVec = embeddingModel.embed("");
        float[] blankVec = embeddingModel.embed("   ");

        assertNotNull(emptyVec);
        assertEquals(384, emptyVec.length);
        assertNotNull(blankVec);
        assertEquals(384, blankVec.length);
    }

    @Test
    @DisplayName("Should embed Spring AI Document and return float array")
    void shouldEmbedSpringAiDocument() {
        Document doc = new Document("Study document text content");
        float[] vector = embeddingModel.embed(doc);

        assertNotNull(vector);
        assertEquals(384, vector.length);
    }

    @Test
    @DisplayName("Should handle EmbeddingRequest and return valid EmbeddingResponse")
    void shouldHandleEmbeddingRequest() {
        EmbeddingRequest request = new EmbeddingRequest(List.of("Chunk one", "Chunk two"), null);
        EmbeddingResponse response = embeddingModel.call(request);

        assertNotNull(response);
        assertNotNull(response.getResults());
        assertEquals(2, response.getResults().size());
        assertEquals(384, response.getResults().get(0).getOutput().length);
        assertEquals(384, response.getResults().get(1).getOutput().length);
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        double dotProduct = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += (v1[i] * v2[i]);
        }
        return dotProduct;
    }
}
