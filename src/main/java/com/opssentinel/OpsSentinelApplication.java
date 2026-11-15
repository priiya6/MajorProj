package com.opssentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OpsSentinel — Multi-Agent LLM Framework for Autonomous Incident Diagnosis
 * and Remediation in Microservices.
 *
 * Built as a modular monolith: the monitoring, diagnosis, remediation, and
 * audit packages are logically separated "agents" that communicate through
 * the DTOs in common.dto — the same contracts as if they were separate
 * services. This keeps the architecture story intact while staying
 * buildable by a solo developer.
 */
@SpringBootApplication
public class OpsSentinelApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsSentinelApplication.class, args);
    }
}
