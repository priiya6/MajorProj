package com.opssentinel.common.dto;

import java.util.List;

/**
 * Produced by: Diagnosis Agent
 * Consumed by: Remediation Agent
 * Matches the "Diagnosis Result" contract in docs/CONTRACTS.md
 */
public record DiagnosisResultDTO(
        String incidentId,
        String rootCause,
        double confidenceScore,
        List<Evidence> evidence,
        String reasoningSummary,
        String recommendedAction,
        String riskLevel,
        java.time.Instant diagnosedAt
) {}
