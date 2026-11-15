package com.opssentinel.common.dto;

import java.time.Instant;

/**
 * Written by every agent, on every decision.
 * Matches the "Audit Log Entry" contract in docs/CONTRACTS.md
 */
public record AuditLogEntryDTO(
        String incidentId,
        String agentName,
        String decision,
        Double confidenceScore,
        String reasoning,
        Instant timestamp
) {}
