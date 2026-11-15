package com.opssentinel.monitoring;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the demo microservice dependency graph.
 * Both CorrelationEngine (deciding which alerts are related) and the
 * dashboard's topology visualization (TopologyController) read from
 * this same map, so the two can never silently drift apart.
 *
 * Replace with real service discovery data for anything beyond the demo.
 */
@Component
public class ServiceTopology {

    /** "who depends on whom" — key depends on each service in its value set. */
    private static final Map<String, Set<String>> DEPENDENCIES = Map.of(
            "order-service", Set.of("payment-service", "inventory-service"),
            "notification-service", Set.of("order-service")
    );

    public boolean dependsOn(String service, String possibleDependency) {
        return DEPENDENCIES.getOrDefault(service, Set.of()).contains(possibleDependency);
    }

    public Map<String, Set<String>> dependencies() {
        return DEPENDENCIES;
    }

    /** Every service mentioned anywhere in the graph, as either a dependent or a dependency. */
    public Set<String> allServices() {
        Set<String> services = new LinkedHashSet<>();
        DEPENDENCIES.forEach((service, deps) -> {
            services.add(service);
            services.addAll(deps);
        });
        return services;
    }
}
