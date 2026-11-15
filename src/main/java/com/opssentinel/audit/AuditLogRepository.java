package com.opssentinel.audit;

import java.util.List;
import com.opssentinel.audit.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByAgentNameAndCreatedAtAfterOrderByCreatedAtAsc(String agentName, java.time.Instant since);
}
