package com.opssentinel.remediation;

import com.opssentinel.audit.AuditLogService;
import com.opssentinel.common.dto.AuditLogEntryDTO;
import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.EscalationTicket;
import com.opssentinel.common.dto.RemediationOutcomeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Remediation Agent.
 *
 * Gate logic: a diagnosis only gets auto-remediated if ALL three hold:
 *  1. confidence >= threshold (configurable, default 0.80)
 *  2. the recommended action is on the pre-approved safe list
 *  3. the model's own risk assessment isn't "high"
 * Otherwise it escalates with a structured ticket explaining exactly
 * which condition(s) failed — never silently drops an incident.
 *
 * Deliberately never touches real infrastructure — "auto-remediation"
 * here means logging what the system would have done. See README.md
 * for why this is a legitimate scope decision, not a limitation.
 */
@Service
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    /**
     * Pre-approved, low-risk actions the system is allowed to take automatically.
     * Must exactly match a subset of DiagnosisService.RECOMMENDED_ACTIONS — the
     * Diagnosis Agent's response schema is constrained to that same enum, so
     * there's no free-text drift between what Gemini can say and what this
     * gate checks for. "rollback_deployment" and "manual_investigation_required"
     * are deliberately excluded — always escalate those, never auto-run them.
     */
    private static final Set<String> SAFE_ACTIONS = Set.of(
            "restart_connection_pool",
            "clear_cache",
            "restart_service",
            "scale_up_replicas",
            "throttle_notifications"
    );

    private final AuditLogService auditLogService;

    @Value("${opssentinel.remediation.confidence-threshold}")
    private double confidenceThreshold;

    public RemediationService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public RemediationOutcomeDTO remediate(DiagnosisResultDTO diagnosis) {
        boolean confident = diagnosis.confidenceScore() >= confidenceThreshold;
        boolean safeAction = SAFE_ACTIONS.contains(diagnosis.recommendedAction());
        boolean notHighRisk = !"high".equalsIgnoreCase(diagnosis.riskLevel());

        Instant now = Instant.now();
        RemediationOutcomeDTO outcome;
        String auditDecision;

        if (confident && safeAction && notHighRisk) {
            executeSimulatedAction(diagnosis);
            outcome = new RemediationOutcomeDTO(
                    diagnosis.incidentId(), "auto_remediated", diagnosis.recommendedAction(), null, now);
            auditDecision = "auto_remediated";
        } else {
            EscalationTicket ticket = buildEscalationTicket(diagnosis, confident, safeAction, notHighRisk);
            outcome = new RemediationOutcomeDTO(
                    diagnosis.incidentId(), "escalated", null, ticket, now);
            auditDecision = "escalated";
        }

        auditLogService.record(new AuditLogEntryDTO(
                diagnosis.incidentId(),
                "remediation-agent",
                auditDecision,
                diagnosis.confidenceScore(),
                gateReasoning(confident, safeAction, notHighRisk),
                now
        ));

        return outcome;
    }

    /** Never touches real infrastructure — logs the action that "would" run. */
    private void executeSimulatedAction(DiagnosisResultDTO diagnosis) {
        log.info("[SIMULATED REMEDIATION] incident={} action={} (confidence={}, risk={})",
                diagnosis.incidentId(), diagnosis.recommendedAction(),
                diagnosis.confidenceScore(), diagnosis.riskLevel());
    }

    private EscalationTicket buildEscalationTicket(DiagnosisResultDTO diagnosis,
                                                     boolean confident, boolean safeAction, boolean notHighRisk) {
        String title = "Review required: " + diagnosis.rootCause();
        String description = """
                Automated remediation was withheld for incident %s.
                Gate result: %s

                Diagnosed root cause: %s
                Confidence: %.2f (threshold: %.2f)
                Recommended action: %s
                Risk level: %s

                Reasoning: %s
                """.formatted(
                diagnosis.incidentId(),
                gateReasoning(confident, safeAction, notHighRisk),
                diagnosis.rootCause(),
                diagnosis.confidenceScore(),
                confidenceThreshold,
                diagnosis.recommendedAction(),
                diagnosis.riskLevel(),
                diagnosis.reasoningSummary()
        );
        return new EscalationTicket(title, description, "on-call-engineer");
    }

    private String gateReasoning(boolean confident, boolean safeAction, boolean notHighRisk) {
        if (confident && safeAction && notHighRisk) {
            return "Passed all gate conditions: confidence above threshold, action on safe list, risk not high";
        }
        StringBuilder reasons = new StringBuilder("Failed gate: ");
        if (!confident) reasons.append("confidence below threshold; ");
        if (!safeAction) reasons.append("action not on pre-approved safe list; ");
        if (!notHighRisk) reasons.append("risk level is high; ");
        return reasons.toString();
    }
}
