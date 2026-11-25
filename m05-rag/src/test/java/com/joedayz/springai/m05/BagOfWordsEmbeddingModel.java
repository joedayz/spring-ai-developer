package com.joedayz.springai.m05;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Embeddings falsos y deterministas: cuentan palabras en buckets. Solo para pruebas sin API key. */
class BagOfWordsEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 256;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> result = new ArrayList<>();
        int i = 0;
        for (String text : request.getInstructions()) {
            result.add(new Embedding(embed(text), i++));
        }
        return new EmbeddingResponse(result);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        String clean = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (String word : clean.split("[^a-z0-9]+")) {
            if (word.length() > 2) {
                v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        return v;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
