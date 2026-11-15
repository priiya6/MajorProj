package com.opssentinel.diagnosis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Converts between a float[] embedding and the JSON string stored in
 * PastIncidentEntity.embeddingJson. Since we're not using pgvector's
 * native vector column type, embeddings are stored as plain JSON arrays
 * and decoded here for similarity computation in Java.
 */
@Component
public class EmbeddingCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String encode(float[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to encode embedding", e);
        }
    }

    public float[] decode(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, float[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode embedding: " + embeddingJson, e);
        }
    }
}
