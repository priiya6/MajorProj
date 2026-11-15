package com.opssentinel.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opssentinel.common.dto.CorrelatedAlert;
import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.Evidence;
import com.opssentinel.common.dto.IncidentDTO;
import com.opssentinel.diagnosis.entity.PastIncidentEntity;
import com.opssentinel.diagnosis.repository.PastIncidentRepository;
import com.opssentinel.diagnosis.util.CosineSimilarity;
import com.opssentinel.diagnosis.util.EmbeddingCodec;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Diagnosis Agent — the core AI component.
 *
 * Pipeline (all stages now implemented):
 *  1. Embed the incoming incident text                — embed()
 *  2. Retrieve top-k similar past incidents (RAG)      — retrieveTopKSimilar()
 *  3. Call Gemini with incident + retrieved evidence,
 *     asking for structured JSON output                — diagnose()
 *
 * See docs/CONTRACTS.md section 4.2 for the exact output shape.
 */
@Service
public class DiagnosisService {

    private final PastIncidentRepository pastIncidentRepository;
    private final EmbeddingCodec embeddingCodec;
    private final GeminiClient geminiClient;
    private final com.opssentinel.audit.AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiagnosisService.class);


    // EDIT 2a: matches below this similarity are dropped instead of being
    // presented to the model as "similar past incidents"
    @Value("${opssentinel.diagnosis.min-evidence-similarity:0.70}")
    private double minEvidenceSimilarity;

    public DiagnosisService(PastIncidentRepository pastIncidentRepository,
                            EmbeddingCodec embeddingCodec,
                            GeminiClient geminiClient,
                            com.opssentinel.audit.AuditLogService auditLogService) {
        this.pastIncidentRepository = pastIncidentRepository;
        this.embeddingCodec = embeddingCodec;
        this.geminiClient = geminiClient;
        this.auditLogService = auditLogService;
    }

    /** Embeds arbitrary text via the Gemini embedding model. */
    public float[] embed(String text) {
        return geminiClient.embedContent(text);
    }

    /**
     * RAG retrieval step. Loads all past incidents, scores each against the
     * query embedding with cosine similarity, drops anything below the
     * minimum similarity, and returns the top-k of what's left as Evidence.
     */
    public List<Evidence> retrieveTopKSimilar(float[] queryEmbedding, int k) {
        List<PastIncidentEntity> corpus = pastIncidentRepository.findAll();

        return corpus.stream()
                .map(entity -> {
                    float[] candidateEmbedding = embeddingCodec.decode(entity.getEmbeddingJson());
                    double similarity = CosineSimilarity.compute(queryEmbedding, candidateEmbedding);
                    return new Evidence(
                            "past_incident_" + entity.getId(),
                            entity.getSummary(),
                            similarity
                    );
                })
                .filter(e -> e.similarityScore() >= minEvidenceSimilarity) // EDIT 2b
                .sorted(Comparator.comparingDouble(Evidence::similarityScore).reversed())
                .limit(k)
                .toList();
    }

    /** Full diagnosis pipeline: embed -> retrieve -> ask Gemini -> structured result. */
    public DiagnosisResultDTO diagnose(IncidentDTO incident) {
        DiagnosisResultDTO diagnosisResult;
        boolean failed = false;

        try {
            String incidentText = describeIncident(incident);

            float[] queryEmbedding = embed(incidentText);
            List<Evidence> evidence = retrieveTopKSimilar(queryEmbedding, 3);

            String prompt = buildPrompt(incidentText, evidence);
            JsonNode result = geminiClient.generateStructured(prompt, buildResponseSchema());

            diagnosisResult = new DiagnosisResultDTO(
                    incident.incidentId(),
                    result.path("rootCause").asText(),
                    result.path("confidenceScore").asDouble(),
                    evidence,
                    result.path("reasoningSummary").asText(),
                    result.path("recommendedAction").asText(),
                    result.path("riskLevel").asText(),
                    java.time.Instant.now()
            );
        } catch (RuntimeException e) {
            // Fail closed: if diagnosis can't run, hand the incident to a human
            // instead of dropping it or guessing.
            failed = true;
            log.error("Diagnosis failed for incident {}", incident.incidentId(), e);
            String detail = String.valueOf(e.getMessage());
            if (e.getCause() != null) {
                detail += " [" + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage() + "]";
            }
            if (detail.length() > 220) {
                detail = detail.substring(0, 220) + "...";
            }
            diagnosisResult = new DiagnosisResultDTO(
                    incident.incidentId(),
                    "Automated diagnosis unavailable (" + detail + ")",
                    0.0,
                    List.of(),
                    "Diagnosis step failed: " + e,
                    "manual_investigation_required",
                    "high",
                    java.time.Instant.now()
            );
        }

        auditLogService.record(new com.opssentinel.common.dto.AuditLogEntryDTO(
                incident.incidentId(),
                "diagnosis-agent",
                failed ? "diagnosis_failed" : "root_cause_identified",
                diagnosisResult.confidenceScore(),
                diagnosisResult.reasoningSummary(),
                java.time.Instant.now()
        ));

        return diagnosisResult;
    }

    private String describeIncident(IncidentDTO incident) {
        return incident.correlatedAlerts().stream()
                .map(this::describeAlert)
                .collect(Collectors.joining("\n"));
    }

    private String describeAlert(CorrelatedAlert alert) {
        return "[%s] %s: %s (severity: %s, at %s)".formatted(
                alert.serviceName(), alert.alertType(), alert.message(),
                alert.severity(), alert.timestamp());
    }
    // EDIT 3: whole method replaced — adds the rules and the empty-evidence message
    private String buildPrompt(String incidentText, List<Evidence> evidence) {
        StringBuilder evidenceBlock = new StringBuilder();
        if (evidence.isEmpty()) {
            evidenceBlock.append("(none found above the similarity threshold)\n");
        }
        for (Evidence e : evidence) {
            evidenceBlock.append("- (similarity %.2f) %s\n".formatted(e.similarityScore(), e.snippet()));
        }

        return """
                You are the Diagnosis Agent in an AIOps incident-response system.
                Determine the most likely root cause of the incident below.

                Correlated alerts for this incident:
                %s

                Similar past incidents (context only, may be empty):
                %s

                Rules:
                1. Base the root cause on what the alerts actually say. Do not name a
                   specific cause (a connection pool, a deployment, a third-party
                   outage) unless the alert text itself supports it.
                2. Past incidents are context, not proof. A similar symptom in a past
                   incident does not mean the cause is the same.
                3. If the alerts show symptoms but do not establish a cause, say so
                   plainly in the root cause, give a low confidence, and choose
                   manual_investigation_required.
                4. Confidence must reflect how directly the alerts support your
                   conclusion, not how plausible it sounds. If you are inferring the
                   cause rather than reading it from the alerts, keep confidence
                   below 0.7.
                5. If the cause is clear but none of the actions below would actually
                   fix it (for example a failure at an external provider), choose
                   manual_investigation_required instead of the nearest-sounding action.

                Respond with your root cause diagnosis, a confidence score between
                0 and 1, a short reasoning summary explaining how the alerts support
                your conclusion, a risk level of "low", "medium", or "high" for taking
                automated action, and a recommended action chosen from EXACTLY this
                list (do not invent a new action name):

                - restart_connection_pool: restart a database/service connection pool
                - clear_cache: clear an in-memory or distributed cache
                - restart_service: restart the affected service process
                - scale_up_replicas: add more instances to absorb load
                - throttle_notifications: rate-limit outbound notifications
                - rollback_deployment: revert the most recent deployment
                - manual_investigation_required: none of the above cleanly applies
                """.formatted(incidentText, evidenceBlock.toString());
    }

    private static final List<String> RECOMMENDED_ACTIONS = List.of(
            "restart_connection_pool",
            "clear_cache",
            "restart_service",
            "scale_up_replicas",
            "throttle_notifications",
            "rollback_deployment",
            "manual_investigation_required"
    );

    private ObjectNode buildResponseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "OBJECT");

        ObjectNode properties = schema.putObject("properties");
        properties.putObject("rootCause").put("type", "STRING");
        properties.putObject("confidenceScore").put("type", "NUMBER");
        properties.putObject("reasoningSummary").put("type", "STRING");

        ObjectNode recommendedAction = properties.putObject("recommendedAction");
        recommendedAction.put("type", "STRING");
        ArrayNode actionEnum = recommendedAction.putArray("enum");
        RECOMMENDED_ACTIONS.forEach(actionEnum::add);

        ObjectNode riskLevel = properties.putObject("riskLevel");
        riskLevel.put("type", "STRING");
        ArrayNode riskEnum = riskLevel.putArray("enum");
        riskEnum.add("low").add("medium").add("high");

        ArrayNode required = schema.putArray("required");
        required.add("rootCause").add("confidenceScore").add("reasoningSummary")
                .add("recommendedAction").add("riskLevel");

        return schema;
    }
}