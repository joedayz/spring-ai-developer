package com.joedayz.springai.m05;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class RagController {

    private final KnowledgeBase knowledgeBase;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor modularAdvisor;
    private final int topK;
    private final double threshold;

    RagController(KnowledgeBase knowledgeBase, VectorStore vectorStore, ChatClient ragClient,
                  RetrievalAugmentationAdvisor modularRagAdvisor,
                  @Value("${rag.top-k:4}") int topK,
                  @Value("${rag.similarity-threshold:0.4}") double threshold) {
        this.knowledgeBase = knowledgeBase;
        this.vectorStore = vectorStore;
        this.chatClient = ragClient;
        this.modularAdvisor = modularRagAdvisor;
        this.topK = topK;
        this.threshold = threshold;
    }

    /** Básico: cargar los documentos Markdown. */
    @PostMapping("/ingest")
    Map<String, Object> ingest() throws IOException {
        return Map.of("chunks", knowledgeBase.ingestMarkdown());
    }

    /** Intermedio: cargar un PDF subido por el usuario. */
    @PostMapping("/ingest/pdf")
    Map<String, Object> ingestPdf(@RequestParam MultipartFile file, @RequestParam String category) throws IOException {
        var resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        return Map.of("chunks", knowledgeBase.ingestPdf(resource, category));
    }

    /** Básico: búsqueda por similitud, sin modelo de chat. */
    @GetMapping("/search")
    List<Map<String, Object>> search(@RequestParam String q, @RequestParam(defaultValue = "3") int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(topK).build()).stream()
                .map(d -> Map.<String, Object>of(
                        "score", d.getScore() == null ? 0.0 : d.getScore(),
                        "source", String.valueOf(d.getMetadata().get("source")),
                        "text", d.getText()))
                .toList();
    }

    /** Intermedio: RAG con QuestionAnswerAdvisor y filtro opcional por metadato. */
    @GetMapping("/ask")
    String ask(@RequestParam String q, @RequestParam(required = false) String category) {
        var request = SearchRequest.builder().topK(topK).similarityThreshold(threshold);
        if (category != null) {
            request.filterExpression("category == '%s'".formatted(category.replace("'", "")));
        }
        return chatClient.prompt()
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).searchRequest(request.build()).build())
                .user(q)
                .call()
                .content();
    }

    /** Avanzado: RAG modular (reescritura de consulta, umbral, sin contexto no responde). */
    @GetMapping("/ask-modular")
    String askModular(@RequestParam String q) {
        return chatClient.prompt().advisors(modularAdvisor).user(q).call().content();
    }
}
