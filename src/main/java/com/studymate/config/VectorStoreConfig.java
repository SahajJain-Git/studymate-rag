package com.studymate.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing the in-memory SimpleVectorStore bean.
 *
 * Concepts for Beginners:
 * - @Configuration: Declares that this class contains Spring Bean definitions.
 * - SimpleVectorStore: An in-memory vector store provided directly by Spring AI.
 *   It calculates cosine similarity on-the-fly without requiring any external database
 *   (such as PostgreSQL/pgvector, Pinecone, or Chroma).
 * - Dependency Injection: Spring injects the configured EmbeddingModel (DeterministicLocalEmbeddingModel
 *   by default) directly into the VectorStore builder.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
