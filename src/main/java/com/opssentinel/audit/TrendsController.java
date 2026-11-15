package com.opssentinel.audit;

import com.opssentinel.audit.entity.AuditLogEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Real trend data for the dashboard sparklines, bucketed into 5-minute
 * windows over the last hour — not fabricated decorative data. Buckets
 * with no incidents are left null (a gap in the line), not zeroed out,
 * since "no data" and "0% resolve rate" are different facts.
 */
@RestController
public class TrendsController {

    private static final int BUCKET_COUNT = 12;
    private static final int BUCKET_MINUTES = 1;

    public record TrendPoint(int incidentCount, Double resolveRate, Double avgConfidence) {}
    public record TrendsResponse(List<TrendPoint> points) {}

    private final AuditLogRepository auditLogRepository;

    public TrendsController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/api/stats/trends")
    public TrendsResponse trends() {
        Instant now = Instant.now();
        Instant windowStart = now.minus(BUCKET_COUNT * BUCKET_MINUTES, ChronoUnit.MINUTES);

        List<AuditLogEntity> rows = auditLogRepository
                .findByAgentNameAndCreatedAtAfterOrderByCreatedAtAsc("remediation-agent", windowStart);

        int[] counts = new int[BUCKET_COUNT];
        int[] resolvedCounts = new int[BUCKET_COUNT];
        double[] confidenceSums = new double[BUCKET_COUNT];

        for (AuditLogEntity row : rows) {
            long minutesFromStart = ChronoUnit.MINUTES.between(windowStart, row.getCreatedAt());
            int bucket = (int) Math.min(BUCKET_COUNT - 1, Math.max(0, minutesFromStart / BUCKET_MINUTES));
            counts[bucket]++;
            if ("auto_remediated".equals(row.getDecision())) resolvedCounts[bucket]++;
            if (row.getConfidenceScore() != null) confidenceSums[bucket] += row.getConfidenceScore();
        }

        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < BUCKET_COUNT; i++) {
            Double resolveRate = counts[i] > 0 ? (double) resolvedCounts[i] / counts[i] : null;
            Double avgConfidence = counts[i] > 0 ? confidenceSums[i] / counts[i] : null;
            points.add(new TrendPoint(counts[i], resolveRate, avgConfidence));
        }

        return new TrendsResponse(points);
    }
}