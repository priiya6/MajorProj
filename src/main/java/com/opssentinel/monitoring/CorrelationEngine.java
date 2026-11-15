package com.opssentinel.monitoring;

import com.opssentinel.common.dto.CorrelatedAlert;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Groups raw alerts into incidents based on:
 *   1. Time proximity — alerts within CORRELATION_WINDOW of each other
 *   2. Service relationship — same service, OR a known dependency between
 *      the two services (see SERVICE_DEPENDENCIES below)
 *
 * This is the literature-cited design choice from the project's survey
 * (graph-based root cause analysis papers) — two alerts are linked if
 * they're close in time AND topologically related, not just close in time.
 *
 * Implementation: union-find over alert indices, linking any pair that
 * satisfies both conditions, then reading off the connected components
 * as the final incident clusters.
 */
@Component
public class CorrelationEngine {

    private static final Duration CORRELATION_WINDOW = Duration.ofSeconds(10);

    private final ServiceTopology serviceTopology;

    public CorrelationEngine(ServiceTopology serviceTopology) {
        this.serviceTopology = serviceTopology;
    }

    public List<List<CorrelatedAlert>> correlate(List<CorrelatedAlert> alerts) {
        int n = alerts.size();
        UnionFind uf = new UnionFind(n);

        List<CorrelatedAlert> sorted = new ArrayList<>(alerts);
        sorted.sort(Comparator.comparing(CorrelatedAlert::timestamp));

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                CorrelatedAlert a = sorted.get(i);
                CorrelatedAlert b = sorted.get(j);
                if (withinWindow(a, b) && areRelated(a, b)) {
                    uf.union(i, j);
                }
            }
        }

        Map<Integer, List<CorrelatedAlert>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = uf.find(i);
            clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(sorted.get(i));
        }

        return new ArrayList<>(clusters.values());
    }

    private boolean withinWindow(CorrelatedAlert a, CorrelatedAlert b) {
        Duration gap = Duration.between(a.timestamp(), b.timestamp()).abs();
        return gap.compareTo(CORRELATION_WINDOW) <= 0;
    }

    private boolean areRelated(CorrelatedAlert a, CorrelatedAlert b) {
        if (a.serviceName().equals(b.serviceName())) {
            return true;
        }
        return dependsOn(a.serviceName(), b.serviceName()) || dependsOn(b.serviceName(), a.serviceName());
    }

    private boolean dependsOn(String service, String possibleDependency) {
        return serviceTopology.dependsOn(service, possibleDependency);
    }

    /** Minimal union-find (disjoint set) with path compression. */
    private static class UnionFind {
        private final int[] parent;

        UnionFind(int size) {
            parent = new int[size];
            Arrays.setAll(parent, i -> i);
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA != rootB) {
                parent[rootA] = rootB;
            }
        }
    }
}
