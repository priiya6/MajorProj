package com.opssentinel.monitoring;

import com.opssentinel.common.dto.IncidentDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demo endpoint — lets you see the Monitoring Agent's output directly in
 * a browser or Postman without writing a test class first. Not meant to
 * survive into the "real" system; remove once monitoring is wired into
 * the actual pipeline.
 */
@RestController
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/api/monitoring/demo")
    public List<IncidentDTO> demo() {
        return monitoringService.generateAndCorrelate();
    }
}
