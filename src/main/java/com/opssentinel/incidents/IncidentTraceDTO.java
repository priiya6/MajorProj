package com.opssentinel.incidents;

import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.IncidentDTO;
import com.opssentinel.common.dto.RemediationOutcomeDTO;

/** Combined view for the dashboard — one incident's full agent relay trace. */
public record IncidentTraceDTO(
        IncidentDTO incident,
        DiagnosisResultDTO diagnosis,
        RemediationOutcomeDTO remediation
) {}
