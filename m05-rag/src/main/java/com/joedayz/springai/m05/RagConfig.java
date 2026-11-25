package com.joedayz.springai.m05;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RagConfig {

    @Bean
    ChatClient ragClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                Eres el asistente de soporte de Acme. Responde en español y de forma breve.
                Usa solo la información del contexto proporcionado.
                """).build();
    }

    /** RAG modular: reescribe la consulta, recupera con umbral y rechaza responder sin contexto. */
    @Bean
    RetrievalAugmentationAdvisor modularRagAdvisor(ChatClient.Builder builder, VectorStore vectorStore,
            @Value("${rag.top-k:4}") int topK,
            @Value("${rag.similarity-threshold:0.4}") double threshold) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(RewriteQueryTransformer.builder()
                        .chatClientBuilder(builder.clone())
                        .build())
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(false)
                        .build())
                .build();
    }
}
