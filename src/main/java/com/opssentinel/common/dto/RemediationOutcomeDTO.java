package com.opssentinel.common.dto;

import java.time.Instant;

/**
 * Produced by: Remediation Agent
 * Consumed by: Dashboard, Audit Layer
 * Matches the "Remediation Outcome" contract in docs/CONTRACTS.md
 *
 * decision is one of: "auto_remediated" | "escalated"
 * Exactly one of actionTaken / escalationTicket should be non-null.
 */
public record RemediationOutcomeDTO(
        String incidentId,
        String decision,
        String actionTaken,
        EscalationTicket escalationTicket,
        Instant timestamp
) {}
