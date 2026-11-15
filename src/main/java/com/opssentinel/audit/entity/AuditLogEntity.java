package com.opssentinel.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Maps directly to the incident_audit_log table described in docs/CONTRACTS.md. */
@Entity
@Table(name = "incident_audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private String incidentId;

    @Column(name = "agent_name", nullable = false)
    private String agentName;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuditLogEntity() {}

    public AuditLogEntity(String incidentId, String agentName, String decision,
                           Double confidenceScore, String reasoning, Instant createdAt) {
        this.incidentId = incidentId;
        this.agentName = agentName;
        this.decision = decision;
        this.confidenceScore = confidenceScore;
        this.reasoning = reasoning;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getAgentName() { return agentName; }
    public String getDecision() { return decision; }
    public Double getConfidenceScore() { return confidenceScore; }
    public String getReasoning() { return reasoning; }
    public Instant getCreatedAt() { return createdAt; }
}
