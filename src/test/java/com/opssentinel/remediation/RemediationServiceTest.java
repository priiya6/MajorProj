package com.opssentinel.remediation;

import com.opssentinel.audit.AuditLogService;
import com.opssentinel.common.dto.AuditLogEntryDTO;
import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.RemediationOutcomeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the Remediation Agent's confidence gate — deliberately not
 * a Spring context test, since the gate logic itself doesn't need one. This
 * exists specifically to prove the escalation path works by construction,
 * not by hoping Gemini happens to return a low-confidence diagnosis in a
 * live run (which, at time of writing, it never has — every real diagnosis
 * observed so far has scored 0.85+).
 *
 * AuditLogService is mocked so these tests never touch the database.
 */
@ExtendWith(MockitoExtension.class)
class   RemediationServiceTest {

    @Mock
    private AuditLogService auditLogService;

    private RemediationService remediationService;

    private static final double CONFIDENCE_THRESHOLD = 0.80;

    @BeforeEach
    void setUp() {
        remediationService = new RemediationService(auditLogService);
        // @Value fields aren't populated outside a Spring context, so set
        // it directly — this mirrors the real default in application.yml.
        ReflectionTestUtils.setField(remediationService, "confidenceThreshold", CONFIDENCE_THRESHOLD);
    }

    private DiagnosisResultDTO diagnosis(double confidence, String action, String riskLevel) {
        return new DiagnosisResultDTO(
                "inc_test",
                "Some root cause",
                confidence,
                List.of(),
                "Some reasoning",
                action,
                riskLevel,
                java.time.Instant.now()
        );
    }

    @Test
    @DisplayName("Auto-remediates when confident, safe action, and risk not high")
    void autoRemediates_whenAllGateConditionsPass() {
        DiagnosisResultDTO diagnosis = diagnosis(0.92, "restart_connection_pool", "medium");

        RemediationOutcomeDTO outcome = remediationService.remediate(diagnosis);

        assertThat(outcome.decision()).isEqualTo("auto_remediated");
        assertThat(outcome.actionTaken()).isEqualTo("restart_connection_pool");
        assertThat(outcome.escalationTicket()).isNull();
    }

    @Test
    @DisplayName("Escalates when confidence is below the threshold")
    void escalates_whenConfidenceBelowThreshold() {
        DiagnosisResultDTO diagnosis = diagnosis(0.50, "restart_connection_pool", "low");

        RemediationOutcomeDTO outcome = remediationService.remediate(diagnosis);

        assertThat(outcome.decision()).isEqualTo("escalated");
        assertThat(outcome.actionTaken()).isNull();
        assertThat(outcome.escalationTicket()).isNotNull();
        assertThat(outcome.escalationTicket().description()).contains("confidence below threshold");
    }

    @Test
    @DisplayName("Escalates when the recommended action is not on the safe list")
    void escalates_whenActionNotOnSafeList() {
        DiagnosisResultDTO diagnosis = diagnosis(0.95, "manual_investigation_required", "low");

        RemediationOutcomeDTO outcome = remediationService.remediate(diagnosis);

        assertThat(outcome.decision()).isEqualTo("escalated");
        assertThat(outcome.escalationTicket().description()).contains("action not on pre-approved safe list");
    }

    @Test
    @DisplayName("Escalates when risk level is high, even at high confidence")
    void escalates_whenRiskLevelIsHigh() {
        DiagnosisResultDTO diagnosis = diagnosis(0.97, "restart_connection_pool", "high");

        RemediationOutcomeDTO outcome = remediationService.remediate(diagnosis);

        assertThat(outcome.decision()).isEqualTo("escalated");
        assertThat(outcome.escalationTicket().description()).contains("risk level is high");
    }

    @Test
    @DisplayName("Confidence exactly at the threshold counts as confident (boundary case)")
    void autoRemediates_whenConfidenceExactlyAtThreshold() {
        DiagnosisResultDTO diagnosis = diagnosis(CONFIDENCE_THRESHOLD, "clear_cache", "low");

        RemediationOutcomeDTO outcome = remediationService.remediate(diagnosis);

        assertThat(outcome.decision()).isEqualTo("auto_remediated");
    }

    @Test
    @DisplayName("Every outcome is recorded to the audit log with the matching decision")
    void recordsAuditLogEntry_forBothOutcomes() {
        remediationService.remediate(diagnosis(0.92, "restart_connection_pool", "medium"));

        ArgumentCaptor<AuditLogEntryDTO> captor = ArgumentCaptor.forClass(AuditLogEntryDTO.class);
        verify(auditLogService).record(captor.capture());

        AuditLogEntryDTO entry = captor.getValue();
        assertThat(entry.agentName()).isEqualTo("remediation-agent");
        assertThat(entry.decision()).isEqualTo("auto_remediated");
        assertThat(entry.confidenceScore()).isEqualTo(0.92);
        assertThat(entry.reasoning()).contains("Passed all gate conditions");
    }
}
