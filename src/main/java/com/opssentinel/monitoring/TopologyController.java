package com.opssentinel.monitoring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exposes the service dependency graph for the dashboard's topology visualization. */
@RestController
public class TopologyController {

    public record Edge(String from, String to) {}
    public record TopologyResponse(List<String> services, List<Edge> dependencies) {}

    private final ServiceTopology serviceTopology;

    public TopologyController(ServiceTopology serviceTopology) {
        this.serviceTopology = serviceTopology;
    }

    @GetMapping("/api/topology")
    public TopologyResponse topology() {
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, java.util.Set<String>> entry : serviceTopology.dependencies().entrySet()) {
            for (String dependency : entry.getValue()) {
                edges.add(new Edge(entry.getKey(), dependency));
            }
        }
        return new TopologyResponse(List.copyOf(serviceTopology.allServices()), edges);
    }
}
