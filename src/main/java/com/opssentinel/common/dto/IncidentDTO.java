package com.opssentinel.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Produced by: Monitoring Agent
 * Consumed by: Diagnosis Agent
 * Matches the "Incident Object" contract in docs/CONTRACTS.md
 */
public record IncidentDTO(
        String incidentId,
        Instant createdAt,
        List<CorrelatedAlert> correlatedAlerts,
        String status,
        Instant processedAt
) {}
