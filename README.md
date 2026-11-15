# OpsSentinel — Starter Project

A multi-agent LLM framework for autonomous incident diagnosis and remediation in microservices, built as a modular monolith for solo development.

## Current setup (as actually running on this machine)

- **Postgres 18** — installed natively on Windows (not Docker), database `opssentinel` created, user `opssentinel` / `devpassword`
- **pgvector — not used.** Docker Desktop had persistent backend failures on this machine, and pgvector has no prebuilt Windows binary (it requires compiling with Visual Studio). Instead, embeddings are stored as plain JSON arrays (`TEXT` column) and similarity is computed in Java — see `diagnosis/util/CosineSimilarity.java`. This is a legitimate design choice at this project's corpus scale, documented as such — not a workaround to hide.
- **RabbitMQ — optional, deferred.** Docker is still broken on this machine. For now, wire the three agents together with direct synchronous Spring service calls (`MonitoringService` → `DiagnosisService` → `RemediationService`, called straight from a controller or a simple orchestrator class). The queue-based design stays documented in `docs/CONTRACTS.md`; swap in real RabbitMQ later once Docker is fixed or if you install it natively.

## Package layout

```
com.opssentinel
├── monitoring/              Monitoring & Correlation Agent      (Week 2)
├── diagnosis/
│   ├── entity/               PastIncidentEntity (RAG corpus)
│   ├── repository/           PastIncidentRepository
│   ├── util/                 EmbeddingCodec, CosineSimilarity   (implemented)
│   └── DiagnosisService       RAG retrieval implemented; embed() and
│                              Claude API call still TODO         (Weeks 3–4)
├── remediation/              Remediation Agent                  (Week 5)
├── audit/
│   ├── entity/                AuditLogEntity
│   ├── AuditLogRepository
│   └── AuditLogService        fully implemented — writes to incident_audit_log
├── common/dto/                Shared contracts between agents (don't change lightly)
└── config/                    Spring configuration (empty — add as needed)
```

## Getting started

1. **Confirm Postgres is running** (Windows Services app → `postgresql-x64-18` → Running)

2. **(Optional) Start RabbitMQ**, only once you're ready to wire real async messaging:
   ```
   cd infra
   docker-compose up -d
   ```
   If Docker is still broken on your machine, skip this — build with direct method calls between agents first.

3. **Set your Claude API key:**
   ```
   cp .env.example .env
   ```
   Fill in `CLAUDE_API_KEY`.

4. **Run the app:**
   ```
   ./mvnw spring-boot:run
   ```

## Suggested build order

1. ~~`audit`~~ — done: `AuditLogEntity` + repository + service are fully implemented
2. `monitoring` — synthetic log generator + simple correlation logic
3. `diagnosis` —
   - ~~RAG retrieval~~ done (`retrieveTopKSimilar` uses cosine similarity against `past_incidents`)
   - TODO: `embed()` — call an embedding API (or a local model) to turn incident text into a `float[]`
   - TODO: the actual Claude API call, prompting for structured JSON matching `DiagnosisResultDTO`
4. `remediation` — confidence gate + simulated action handler
5. Wire `monitoring → diagnosis → remediation` together — direct calls for now, swap to RabbitMQ queues later if Docker gets fixed
6. Build the React dashboard

## Seeding sample data for RAG retrieval

Before `retrieveTopKSimilar` returns anything useful, insert a few rows into `past_incidents` manually via psql or pgAdmin, e.g.:
```sql
INSERT INTO past_incidents (summary, root_cause, embedding)
VALUES ('Payment service connection pool exhausted during peak load',
        'DB connection pool ran out of available connections',
        '[0.01, -0.02, 0.03, ...]');
```
The embedding array needs to match whatever dimension your chosen embedding model produces (e.g. 1536 for many OpenAI-compatible models) — generate real ones via your `embed()` implementation once it's wired up, this is just placeholder shape.

## Notes

- The Remediation Agent should never touch real infrastructure — the action handler logs what it "would" do.
- Keep `docs/CONTRACTS.md` next to this code and update it the moment any DTO shape changes.
