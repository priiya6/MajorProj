package com.opssentinel.audit;

import com.opssentinel.common.dto.AuditLogEntryDTO;
import com.opssentinel.audit.entity.AuditLogEntity;
import org.springframework.stereotype.Service;

/**
 * Audit & Explainability Layer.
 *
 * Every agent calls record() after every decision — this is what makes the
 * system defensible in your viva as "not a black box."
 *
 * See docs/CONTRACTS.md section 4.4 for the exact entry shape.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(AuditLogEntryDTO entry) {
        AuditLogEntity entity = new AuditLogEntity(
                entry.incidentId(),
                entry.agentName(),
                entry.decision(),
                entry.confidenceScore(),
                entry.reasoning(),
                entry.timestamp()
        );
        repository.save(entity);
    }
}
