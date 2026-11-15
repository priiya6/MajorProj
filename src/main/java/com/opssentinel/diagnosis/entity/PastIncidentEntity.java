package com.opssentinel.diagnosis.entity;

import jakarta.persistence.*;

/**
 * A past incident/runbook entry used as the RAG retrieval corpus for the
 * Diagnosis Agent. The embedding is stored as a JSON array string (e.g.
 * "[0.012, -0.048, ...]") rather than a native pgvector column — see
 * EmbeddingCodec for the conversion, and README.md for why.
 */
@Entity
@Table(name = "past_incidents")
public class PastIncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "embedding", columnDefinition = "TEXT", nullable = false)
    private String embeddingJson;

    public PastIncidentEntity() {}

    public PastIncidentEntity(String summary, String rootCause, String embeddingJson) {
        this.summary = summary;
        this.rootCause = rootCause;
        this.embeddingJson = embeddingJson;
    }

    public Long getId() { return id; }
    public String getSummary() { return summary; }
    public String getRootCause() { return rootCause; }
    public String getEmbeddingJson() { return embeddingJson; }
}
