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

## Final thoughts

I set out to build something **small enough to read in an afternoon** but **honest enough** to touch real distributed concerns: DAG validation, cross-service calls, retries, saga-style compensation, and basic ops hooks (metrics, optional traces).

### Central orchestration and Postgres as source of truth

I wanted one obvious place to answer “what runs next?” without pulling in a message bus or a full workflow engine on day one. The database holds workflow and task state; the orchestrator loads it, computes runnable tasks, dispatches over gRPC, and writes outcomes back. That keeps the control flow traceable even when workers and networks misbehave.

### Submission, completion, and Spring events

The orchestrator needs to wake up in two situations:

| When | What should happen |
|------|-------------------|
| Someone **submitted** a workflow | Start any tasks that can run right away (no dependencies yet). |
| A task **finished successfully** | Save that in the DB, then look again for newly runnable tasks (e.g. task **b** after **a** succeeds). |

Both use “events,” but **the second one is easier to get wrong**, so it is worth spelling out.

**Why wait until after commit on submit?**  
If the orchestrator started work in the **same** database transaction as `save`, a rollback could cancel the insert while workers were already running. So I fire `WorkflowSubmittedEvent` and listen with **`@TransactionalEventListener(AFTER_COMMIT)`** — meaning “only run this **after** Postgres has really stored the workflow and tasks.”

**Common bugs with “task b never ran”**  
Task **b** waits until **a** is **SUCCESS** in the database. The worker can finish **a** fine; the bug is on the orchestrator side: something must **re-run scheduling** after `a` is committed.

I first published **`TaskCompletedEvent`** from **`GrpcWorkerTaskDispatcher`** right after **`WorkerDispatchPersistence.markSuccess`**, with **`@TransactionalEventListener(AFTER_COMMIT)`** like submit. That fails for two common reasons:

1. **Dispatch runs on a pool thread** (`dispatchExecutor` in **`WorkflowExecutionService`**). That thread usually has **no** Spring transaction when it calls `publishEvent`. For **`AFTER_COMMIT`**, Spring then has **nothing to commit to** and **skips** the listener unless `fallbackExecution = true`.
2. Even on a thread that still has an **outer** read-only transaction, the listener is tied to **that** transaction’s commit, not to the **`REQUIRES_NEW`** transaction that actually wrote `SUCCESS` — easy to get **surprising timing** or **missed** notifications.

**Symptom:** worker log shows **a** succeeded; task **b** stays **PENDING** forever.

**What the code does now**  
1. **`markSuccess`** (`REQUIRES_NEW`) saves `SUCCESS`, then registers **`TransactionSynchronization.afterCommit`** so **`TaskCompletedEvent`** is published **only after** that inner transaction **commits**.  
2. **`WorkflowExecutionListener.onTaskCompleted`** is a plain **`@EventListener`**, so it always runs when the event is published — no dependency on “which outer transaction is open.”  
3. **`onWorkflowSubmitted`** stays **`@TransactionalEventListener(AFTER_COMMIT)`** because **`WorkflowSubmissionService`** publishes **`WorkflowSubmittedEvent`** **inside** the submit transaction.

**Hypothetical run**  
Workflow **demo** with tasks **`a`** (no deps) then **`b`** (depends on **`a`**):

1. **POST /workflows** → **`WorkflowSubmissionService`** persists rows in **transaction T_submit**, then `publishEvent(new WorkflowSubmittedEvent("demo"))`.  
2. After **T_submit** commits, **`WorkflowExecutionListener.onWorkflowSubmitted`** runs and calls **`WorkflowExecutionService.triggerExecution("demo")`**.  
3. **`triggerExecution`** opens a **read-only transaction T_read**, loads tasks, sees only **`a`** runnable, and submits **`GrpcWorkerTaskDispatcher.dispatch(a)`** to **`dispatchExecutor`** (another thread). **`triggerExecution`** returns and **T_read** ends.  
4. On the **pool thread**, dispatch talks to the worker; on success it calls **`WorkerDispatchPersistence.markSuccess(idForA)`**.  
5. **`markSuccess`** runs in **T_success** (`REQUIRES_NEW`), writes **`a = SUCCESS`**, commits **T_success**, then the registered **`afterCommit`** runs and **`publishEvent(new TaskCompletedEvent("demo", "a"))`**.  
6. **`WorkflowExecutionListener.onTaskCompleted`** runs and calls **`triggerExecution("demo")`** again. This time the DB shows **`a`** as **SUCCESS**, so **`b`** is runnable and gets dispatched the same way.

Step 5 is the fix: the completion signal is **pinned to the commit of the transaction that actually wrote `SUCCESS`**, and the listener does **not** rely on **`TransactionalEventListener`**.

**Takeaway**  
Same word “event,” two shapes: submit uses **“after my DB transaction commits”**; task completion uses **“after the success-write transaction commits”** plus a **non-transactional** listener so pool threads and `REQUIRES_NEW` do not silently drop the notification.

### Retries, leases, and not blocking threads

I did not want long **`Thread.sleep`** loops holding HTTP or arbitrary threads open. Failures schedule **`next_retry_at`**; a scheduler reapplies work; a **time-based lease** reduces duplicate dispatch while a call is in flight and eventually recovers if a JVM dies mid-flight. The model is **at-least-once**; a real deployment would push **idempotent** task handlers—I kept the sample worker stubby on purpose.

### Workers, gRPC, and “just enough” scale

Workers stay **thin**: accept RPC, run the hook, return success or failure. The orchestrator can target **multiple worker endpoints** with a simple **round-robin** client—enough to show horizontal scale without inventing discovery, sticky routing, or per-backend circuit breakers. Resilience4j still protects the client path, but I treat that as a demo-grade compromise.

### What I deliberately left out

No outbox/inbox hardening, no Temporal/Camunda/Step Functions, no exactly-once story, no production-grade multi-tenant ops. Drawing that line keeps the repo useful as a **map of the territory** before you invest in platform-sized tooling.

## Other commands

```bash
./gradlew test          # run tests
./gradlew build         # compile and test
```