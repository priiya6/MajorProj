package com.opssentinel.common.dto;

import java.time.Instant;

/** One alert that the Monitoring Agent has grouped into an incident. */
public record CorrelatedAlert(
        String serviceName,
        String alertType,
        String message,
        Instant timestamp,
        String severity
) {}
