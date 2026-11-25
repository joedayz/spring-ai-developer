package com.joedayz.springai.m05;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

/** Pipeline ETL: leer (Extract), dividir en chunks (Transform) y guardar con embeddings (Load). */
@Service
class KnowledgeBase {

    static final String KB = "kb";

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resolver;
    private final TokenTextSplitter splitter;

    KnowledgeBase(VectorStore vectorStore, ResourcePatternResolver resolver,
                  @Value("${rag.chunk-size:200}") int chunkSize) {
        this.vectorStore = vectorStore;
        this.resolver = resolver;
        this.splitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
    }

    /** Carga los .md de classpath:docs. Es idempotente: borra lo anterior de la misma fuente. */
    int ingestMarkdown() throws java.io.IOException {
        List<Document> chunks = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath:docs/*.md")) {
            var reader = new TextReader(resource);
            String category = resource.getFilename().replace(".md", "");
            reader.getCustomMetadata().put("category", category);
            reader.getCustomMetadata().put(KB, "acme");
            chunks.addAll(splitter.apply(reader.get()));
        }
        replace("acme", chunks);
        return chunks.size();
    }

    /** Carga un PDF página por página. */
    int ingestPdf(Resource pdf, String category) {
        var documents = new PagePdfDocumentReader(pdf).get();
        documents.forEach(d -> {
            d.getMetadata().put("category", category);
            d.getMetadata().put(KB, "pdf-" + category);
        });
        List<Document> chunks = splitter.apply(documents);
        replace("pdf-" + category, chunks);
        return chunks.size();
    }

    private void replace(String kb, List<Document> chunks) {
        vectorStore.delete("%s == '%s'".formatted(KB, kb));
        vectorStore.add(chunks);
    }
}
