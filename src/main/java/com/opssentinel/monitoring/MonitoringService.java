package com.opssentinel.monitoring;

import com.opssentinel.audit.AuditLogService;
import com.opssentinel.common.dto.AuditLogEntryDTO;
import com.opssentinel.common.dto.CorrelatedAlert;
import com.opssentinel.common.dto.IncidentDTO;
import com.opssentinel.monitoring.generator.SyntheticLogGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Monitoring & Correlation Agent.
 *
 * Pipeline:
 *  1. Ingest raw alerts (synthetic generator for now — see generator/)
 *  2. Correlate related alerts within a time window across dependent
 *     services (see CorrelationEngine)
 *  3. Package each correlated cluster as an IncidentDTO
 *
 * See docs/CONTRACTS.md section 4.1 for the exact output shape.
 */
@Service
public class MonitoringService {

    private final CorrelationEngine correlationEngine;
    private final AuditLogService auditLogService;
    private final SyntheticLogGenerator syntheticLogGenerator = new SyntheticLogGenerator();

    public MonitoringService(CorrelationEngine correlationEngine, AuditLogService auditLogService) {
        this.correlationEngine = correlationEngine;
        this.auditLogService = auditLogService;
    }

    /**
     * Core logic — fully implemented. Takes raw alerts from any source and
     * returns one IncidentDTO per correlated cluster.
     */
    public List<IncidentDTO> buildIncidents(List<CorrelatedAlert> rawAlerts) {
        List<List<CorrelatedAlert>> clusters = correlationEngine.correlate(rawAlerts);

        return clusters.stream()
                .map(this::toIncident)
                .toList();
    }

    /**
     * Convenience entry point for local testing/demo — pulls from the
     * synthetic generator instead of a real ingestion source. Swap this
     * out once real log/metric ingestion is wired up.
     */
    public List<IncidentDTO> generateAndCorrelate() {
        List<CorrelatedAlert> rawAlerts = syntheticLogGenerator.generateBatch();
        return buildIncidents(rawAlerts);
    }

    private IncidentDTO toIncident(List<CorrelatedAlert> cluster) {
        List<CorrelatedAlert> sorted = cluster.stream()
                .sorted(Comparator.comparing(CorrelatedAlert::timestamp))
                .toList();

        Instant earliest = sorted.get(0).timestamp();
        String incidentId = "inc_" + UUID.randomUUID().toString().substring(0, 8);

        auditLogService.record(new AuditLogEntryDTO(
                incidentId,
                "monitoring-agent",
                "incident_correlated",
                null,
                "Correlated %d alert(s) from service(s): %s".formatted(
                        sorted.size(),
                        sorted.stream().map(CorrelatedAlert::serviceName).distinct().reduce((a, b) -> a + ", " + b).orElse("")
                ),
                Instant.now()
        ));

        return new IncidentDTO(incidentId, earliest, sorted, "new", java.time.Instant.now());
    }
}
