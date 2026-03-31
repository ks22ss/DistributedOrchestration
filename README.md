# DistributedOrchestration

Sample **distributed DAG workflow orchestrator** in Java: a Spring Boot **orchestrator** (REST + PostgreSQL + gRPC client) dispatches tasks to a Spring Boot **worker** (gRPC server).

## Prerequisites

- **JDK 21** (Gradle uses the toolchain resolver; `./gradlew` will suggest downloads if needed)
- **Docker** (optional, for PostgreSQL, Prometheus, and Jaeger)

`bootRun` for both apps attaches the **OpenTelemetry Java agent** from `common/lib/opentelemetry-javaagent.jar`. If that file is missing, copy an agent JAR from the [OpenTelemetry Java Instrumentation releases](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases) into `common/lib/` with that name, or JVM startup will fail.

## Run supporting services (Docker)

From the repo root:

```bash
docker compose up -d
```

This starts:

| Service    | Purpose | On your machine |
|------------|---------|-----------------|
| PostgreSQL | Orchestrator database | `localhost:5432` — DB `orchestration`, user/password `orchestrator` |
| Prometheus | Scrapes orchestrator metrics | UI: `http://localhost:9099` (container `9090` mapped to host `9099`) |
| Jaeger     | OTLP trace backend | UI: `http://localhost:16686` — OTLP gRPC: `localhost:4317` |

Prometheus is configured to scrape the orchestrator at `host.docker.internal:8080` (`docker/prometheus/prometheus.yml`). Run the orchestrator on port **8080** (default) so scrapes succeed.

Override the OTLP endpoint when starting the apps if Jaeger is not on localhost:

```bash
./gradlew :orchestrator:bootRun -Potel.otlp.endpoint=http://localhost:4317
```

## Run the applications

Use **two terminals**. Start infrastructure first if you rely on Docker for Postgres.

1. **Worker** (gRPC on **9090**, HTTP management on **8081**):

   ```bash
   ./gradlew :worker:bootRun
   ```

2. **Orchestrator** (REST and Actuator on **8080**; targets workers via `orchestration.worker.host` / `grpc-port`, or comma-separated `orchestration.worker.endpoints` for round-robin across multiple workers — see `orchestrator/src/main/resources/application.yml`):

   ```bash
   ./gradlew :orchestrator:bootRun
   ```

Submit workflows with `POST http://localhost:8080/workflows` (JSON body per `WorkflowController` and the DTOs in the orchestrator module).

Task dispatch retries use **`next_retry_at`** in the database and a periodic retry scan (`orchestration.dispatch.retry-scan-interval-ms` in `application.yml`), not `Thread.sleep` on the HTTP thread. An in-flight **lease** (`in-flight-lease-seconds`) prevents duplicate dispatch until a call completes, backoff elapses, or the lease expires after a crash. Independent runnable tasks in the same wave run on a bounded **dispatch pool** (`parallelism`, `parallel-queue-capacity`).

## Other commands

```bash
./gradlew test          # run tests
./gradlew build         # compile and test
```