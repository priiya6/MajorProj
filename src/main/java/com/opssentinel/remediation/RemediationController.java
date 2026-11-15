package com.opssentinel.remediation;

import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.IncidentDTO;
import com.opssentinel.common.dto.RemediationOutcomeDTO;
import com.opssentinel.diagnosis.DiagnosisService;
import com.opssentinel.monitoring.MonitoringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demo endpoint — runs the FULL pipeline: Monitoring -> Diagnosis ->
 * Remediation, end to end, with a real Gemini call in the middle.
 * Requires GEMINI_API_KEY to be set and at least one seeded past incident
 * for the RAG step to have something to retrieve (see /api/diagnosis/seed).
 */
@RestController
public class RemediationController {

    private final MonitoringService monitoringService;
    private final DiagnosisService diagnosisService;
    private final RemediationService remediationService;

    public RemediationController(MonitoringService monitoringService,
                                  DiagnosisService diagnosisService,
                                  RemediationService remediationService) {
        this.monitoringService = monitoringService;
        this.diagnosisService = diagnosisService;
        this.remediationService = remediationService;
    }

    @GetMapping("/api/remediation/demo")
    public List<RemediationOutcomeDTO> demo() {
        List<IncidentDTO> incidents = monitoringService.generateAndCorrelate();
        return incidents.stream()
                .map(diagnosisService::diagnose)
                .map(remediationService::remediate)
                .toList();
    }
}
