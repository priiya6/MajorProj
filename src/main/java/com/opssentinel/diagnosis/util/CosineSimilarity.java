package com.opssentinel.diagnosis.util;

/**
 * Plain-Java replacement for pgvector's <=> cosine distance operator.
 * Fine for the corpus sizes this project actually operates at — see
 * README.md for the reasoning. Swap for pgvector later if you ever need
 * indexed approximate nearest-neighbor search at real scale.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {}

    public static double compute(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
