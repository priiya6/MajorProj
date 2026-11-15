package com.opssentinel.monitoring.generator;

import com.opssentinel.common.dto.CorrelatedAlert;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Produces realistic-looking alert data without needing a real microservice
 * deployment. Rotates through several failure scenarios, one per call, so
 * repeated demo runs show different stories instead of the same one.
 *
 * MonitoringService holds one instance of this class, so the rotation
 * counter persists across calls.
 */
public class SyntheticLogGenerator {

    private static final int SCENARIO_COUNT = 3;
    private final AtomicInteger nextScenario = new AtomicInteger();

    public List<CorrelatedAlert> generateBatch() {
        Instant now = Instant.now();
        int scenario = Math.floorMod(nextScenario.getAndIncrement(), SCENARIO_COUNT);
        return switch (scenario) {
            case 0 -> poolExhaustion(now);
            case 1 -> thirdPartyOutage(now);
            default -> ambiguousLatency(now);
        };
    }

    /** Clear-cut: payment DB pool runs dry, order-service times out downstream. */
    private List<CorrelatedAlert> poolExhaustion(Instant now) {
        return List.of(
                new CorrelatedAlert("payment-service", "db_connection_failure",
                        "Connection pool exhausted: 0/20 available", now, "high"),
                new CorrelatedAlert("order-service", "downstream_error",
                        "Payment call failed with timeout", now.plusSeconds(jitter(1, 4)), "medium"),
                // Noise: unrelated service, well outside the correlation window
                new CorrelatedAlert("notification-service", "rate_limit_warning",
                        "SMS provider rate limit at 80% capacity", now.plusSeconds(120), "low")
        );
    }

    /**
     * External cause: the payment gateway itself is failing, and the failure
     * cascades three services deep. A different diagnosis from pool exhaustion,
     * since nothing here is fixed by restarting our own components.
     */
    private List<CorrelatedAlert> thirdPartyOutage(Instant now) {
        return List.of(
                new CorrelatedAlert("payment-service", "upstream_timeout",
                        "External payment gateway returning 504 for 92% of requests", now, "high"),
                new CorrelatedAlert("order-service", "downstream_error",
                        "Checkout failing: payment authorization timed out", now.plusSeconds(jitter(1, 3)), "high"),
                new CorrelatedAlert("notification-service", "delivery_delay",
                        "Order confirmation queue backing up: 340 pending", now.plusSeconds(jitter(4, 6)), "medium"),
                // Noise
                new CorrelatedAlert("inventory-service", "cache_hit_ratio_low",
                        "Cache hit ratio dropped to 61%", now.plusSeconds(120), "low")
        );
    }

    /**
     * Deliberately ambiguous: latency is up on two linked services, but nothing
     * in the alerts points to a single clear cause or a safe automatic fix.
     * Gives the confidence gate a realistic reason to escalate.
     */
    private List<CorrelatedAlert> ambiguousLatency(Instant now) {
        return List.of(
                new CorrelatedAlert("inventory-service", "latency_spike",
                        "p99 latency on stock lookups at 2.4s (baseline 300ms)", now, "medium"),
                new CorrelatedAlert("order-service", "elevated_latency",
                        "Checkout p99 latency 3.1s with intermittent 5xx on inventory calls",
                        now.plusSeconds(jitter(1, 3)), "medium"),
                // Noise
                new CorrelatedAlert("notification-service", "rate_limit_warning",
                        "SMS provider rate limit at 80% capacity", now.plusSeconds(120), "low")
        );
    }

    private long jitter(long minSeconds, long maxSeconds) {
        return ThreadLocalRandom.current().nextLong(minSeconds, maxSeconds + 1);
    }
}