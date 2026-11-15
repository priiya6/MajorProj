package com.opssentinel.diagnosis;

import com.opssentinel.common.dto.DiagnosisResultDTO;
import com.opssentinel.common.dto.IncidentDTO;
import com.opssentinel.diagnosis.entity.PastIncidentEntity;
import com.opssentinel.diagnosis.repository.PastIncidentRepository;
import com.opssentinel.diagnosis.util.EmbeddingCodec;
import com.opssentinel.monitoring.MonitoringService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Demo + utility endpoints for the Diagnosis Agent.
 * Requires GEMINI_API_KEY to be set — see .env.example.
 */
@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController {

    private final MonitoringService monitoringService;
    private final DiagnosisService diagnosisService;
    private final PastIncidentRepository pastIncidentRepository;
    private final EmbeddingCodec embeddingCodec;

    public DiagnosisController(MonitoringService monitoringService,
                                DiagnosisService diagnosisService,
                                PastIncidentRepository pastIncidentRepository,
                                EmbeddingCodec embeddingCodec) {
        this.monitoringService = monitoringService;
        this.diagnosisService = diagnosisService;
        this.pastIncidentRepository = pastIncidentRepository;
        this.embeddingCodec = embeddingCodec;
    }

    /**
     * Runs the Monitoring Agent's synthetic scenario straight through the
     * Diagnosis Agent — the full monitoring -> diagnosis chain, including
     * a real Gemini API call.
     */
    @GetMapping("/demo")
    public List<DiagnosisResultDTO> demo() {
        List<IncidentDTO> incidents = monitoringService.generateAndCorrelate();
        return incidents.stream().map(diagnosisService::diagnose).toList();
    }

    /**
     * Seeds one row into past_incidents, embedding the summary via the
     * real Gemini embedding model. Use this to populate real RAG data
     * instead of hand-crafting fake embedding arrays. Example body:
     * {"summary": "Payment service pool exhausted during peak load",
     *  "rootCause": "DB connection pool ran out of available connections"}
     */
    @PostMapping("/seed")
    public String seed(@RequestBody SeedIncidentRequest request) {
        float[] embedding = diagnosisService.embed(request.summary());
        PastIncidentEntity entity = new PastIncidentEntity(
                request.summary(),
                request.rootCause(),
                embeddingCodec.encode(embedding)
        );
        PastIncidentEntity saved = pastIncidentRepository.save(entity);
        return "Seeded past_incidents id=" + saved.getId();
    }
}
