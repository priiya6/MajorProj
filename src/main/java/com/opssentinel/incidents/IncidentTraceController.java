package com.opssentinel.incidents;

import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.IncidentDTO;
import com.opssentinel.common.dto.RemediationOutcomeDTO;
import com.opssentinel.diagnosis.DiagnosisService;
import com.opssentinel.monitoring.MonitoringService;
import com.opssentinel.remediation.RemediationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Powers the dashboard — the full Monitoring -> Diagnosis -> Remediation trace, combined. */
@RestController
public class IncidentTraceController {

    private final MonitoringService monitoringService;
    private final DiagnosisService diagnosisService;
    private final RemediationService remediationService;

    public IncidentTraceController(MonitoringService monitoringService,
                                    DiagnosisService diagnosisService,
                                    RemediationService remediationService) {
        this.monitoringService = monitoringService;
        this.diagnosisService = diagnosisService;
        this.remediationService = remediationService;
    }

    @GetMapping("/api/incidents/demo")
    public List<IncidentTraceDTO> demo() {
        List<IncidentDTO> incidents = monitoringService.generateAndCorrelate();
        return incidents.stream()
                .map(incident -> {
                    // Sequential demo path: stamp the moment THIS incident's diagnosis begins,
                    // so durations measure its own work and not time spent waiting in line
                    IncidentDTO handedOff = new IncidentDTO(incident.incidentId(), incident.createdAt(),
                            incident.correlatedAlerts(), incident.status(), java.time.Instant.now());
                    DiagnosisResultDTO diagnosis = diagnosisService.diagnose(handedOff);
                    RemediationOutcomeDTO remediation = remediationService.remediate(diagnosis);
                    return new IncidentTraceDTO(handedOff, diagnosis, remediation);
                })
                .toList();
    }
}
