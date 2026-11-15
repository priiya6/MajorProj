package com.opssentinel.common.dto;

/** Populated only when the Remediation Agent escalates instead of auto-fixing. */
public record EscalationTicket(
        String title,
        String description,
        String assignedTo
) {}
