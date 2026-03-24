---
date: 2026-03-24T07:51:20Z
researcher: Cursor Agent
git_commit: d1ec6321d34065ae8adcea5c6fe1a5a776735210
branch: HEAD (detached)
repository: DistributedOrchestration
remote: https://github.com/ks22ss/DistributedOrchestration.git
topic: "Codebase overview — distributed workflow orchestration"
tags: [research, codebase, orchestrator, worker, common, grpc, saga, postgres]
status: complete
last_updated: 2026-03-24
last_updated_by: Cursor Agent
---

# Research: DistributedOrchestration codebase

**Date**: 2026-03-24T07:51:20Z  
**Git commit**: `d1ec6321d34065ae8adcea5c6fe1a5a776735210`  
**Branch**: detached `HEAD` at time of research  
**Repository**: [ks22ss/DistributedOrchestration](https://github.com/ks22ss/DistributedOrchestration)

## Research question

Document the repository as implemented today: modules, execution flow, persistence, gRPC, compensation, configuration, and how it relates to the root `spec.md`.

## Summary

This is a **Gradle multi-module Java 21** project with three modules: **`common`** (domain models + protobuf/gRPC stubs), **`orchestrator`** (Spring Boot REST API, JPA/Flyway, scheduling, gRPC client to workers, saga-style compensation), and **`worker`** (Spring Boot + embedded Netty gRPC server exposing `WorkerService`). Workflows are **DAGs** of tasks stored in **PostgreSQL**; the orchestrator validates the graph on submit, persists state, then after transaction commit selects **runnable** `PENDING` tasks (dependencies all `SUCCESS`) and dispatches them over gRPC. Failures after retry exhaustion trigger **asynchronous compensation** in an order derived from successful tasks (reverse topological). **Micrometer** timers/counters in `OrchestrationMetrics` instrument dispatch and compensation. **Optional local infra**: `docker-compose` runs Postgres and Prometheus; Prometheus scrapes the orchestrator actuator endpoint on the host.

## Detailed findings

### Repository layout and build

- **`settings.gradle.kts`**: root name `DistributedOrchestration`; includes `common`, `orchestrator`, `worker`; Foojay toolchain resolver.
- **`build.gradle.kts`**: `group` `org.example.distributedorchestration`, version `0.1.0-SNAPSHOT`; subprojects use Java 21 toolchain; Spring Boot / protobuf plugins are declared at root with `apply false`.
- **`gradle.properties`**: configuration cache enabled.
- **`gradle/libs.versions.toml`**: version catalog for dependencies (referenced by module build files).

Both **`orchestrator`** and **`worker`** depend on **`project(":common")`** for shared types and generated proto classes.

### `spec.md` (root)

`spec.md` is an **implementation-oriented design document** for a distributed workflow orchestrator (DAG execution, idempotency, retry, compensation, gRPC, observability, K8s). It describes intended goals and patterns; the code follows the same broad structure (orchestrator / worker / common, Flyway schema, `WorkerService`, saga compensation). It is **not** generated from the code; it is a hand-written spec co-located with the repo.

### `common` module

| Area | Location |
|------|----------|
| Proto + API | `common/src/main/proto/worker.proto` |
| Domain | `common/src/main/java/.../common/model/` (`Task`, `Workflow`, `TaskStatus`, `WorkflowStatus`) |
| Executor SPI | `common/src/main/java/.../common/execution/TaskExecutor.java` |

The gRPC service and messages are defined in protobuf:

```9:12:common/src/main/proto/worker.proto
service WorkerService {
  rpc ExecuteTask(TaskRequest) returns (TaskResponse);
  rpc CompensateTask(CompensationRequest) returns (TaskResponse);
}
```

Generated Java lives under `org.example.distributedorchestration.common.worker.v1` (e.g. `WorkerServiceGrpc`, `TaskRequest`, `CompensationRequest`, `TaskResponse`). The **orchestrator** uses these as the **client**; the **worker** implements `WorkerServiceGrpc.WorkerServiceImplBase`.

### `worker` module

- **Entry**: `worker/.../WorkerApplication.java` — standard `@SpringBootApplication`.
- **gRPC handler**: `WorkerGrpcService` — `executeTask` / `compensateTask` delegate to `DefaultTaskExecutor` factories and map results to `TaskResponse`.
- **Task behavior**: `DefaultTaskExecutor` implements `TaskExecutor`; `execute()` / `compensate()` are stub behaviors (e.g. blank `taskId` throws; empty `compensationPayload` short-circuits compensation).
- **Server lifecycle**: Two classes exist that can start a Netty gRPC server on a configurable port (default `9090`):
  - `GrpcServerLifecycle` — `@PostConstruct` / `@PreDestroy`
  - `WorkerGrpcServerLifecycle` — `SmartLifecycle` with late phase  
  Both wire the same `WorkerGrpcService`. Which one effectively governs startup depends on Spring component scanning and bean presence (both are present in the tree as of this commit).
- **HTTP**: `application.yml` sets `server.port` **8081** (separate from orchestrator’s default 8080). Management exposes `health`, `info`, `prometheus`.

### `orchestrator` module

#### REST API

- **`WorkflowController`**: `POST /workflows` → `WorkflowSubmissionService.submit`, returns **201** with `SubmitWorkflowResponse`.

```16:30:orchestrator/src/main/java/org/example/distributedorchestration/orchestrator/api/WorkflowController.java
@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowSubmissionService workflowSubmissionService;

    /**
     * Submits a workflow: validates DAG, persists workflow and tasks, then triggers execution after commit.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitWorkflowResponse submit(@Valid @RequestBody SubmitWorkflowRequest request) {
        return workflowSubmissionService.submit(request);
    }
}
```

- **DTOs**: `SubmitWorkflowRequest`, `SubmitWorkflowTaskDto` (task id, dependencies, payload, optional `compensationPayload`), `SubmitWorkflowResponse`.
- **Errors**: `GlobalExceptionHandler` maps `InvalidWorkflowException` → 400, `DuplicateWorkflowException` → 409, validation errors → 400.

#### Submit → execute pipeline

1. **`WorkflowSubmissionService`**: transactional submit — duplicate `workflowId` rejected, DAG validated via **`WorkflowDagValidator`**, **`WorkflowEntity`** saved as `RUNNING`, **`TaskEntity`** rows as `PENDING`, **`WorkflowSubmittedEvent`** published.
2. **`WorkflowExecutionListener`**: `@TransactionalEventListener(phase = AFTER_COMMIT)` publishes into **`WorkflowExecutionService.triggerExecution`** so execution runs after the submit transaction completes.
3. **`WorkflowExecutionService`**: reads entities, maps to **`common`** `Workflow` / `Task`, **`RunnableTaskSelector.findRunnableTasks`**, then **`WorkerTaskDispatcher.dispatch`** per runnable task.

#### Scheduler and DAG

- **`WorkflowDagValidator`**: non-empty tasks, dependencies refer to existing task ids, **cycle detection (DFS)**; throws **`InvalidWorkflowException`**.
- **`RunnableTaskSelector`**: returns `PENDING` tasks whose dependencies are all **`SUCCESS`** (uses `Workflow.successfulTasks()` and task dependency helpers).
- **`CompensationRecoveryScheduler`**: `@Scheduled` fixed delay from `orchestration.compensation.recovery-interval-ms`; finds workflows in **`COMPENSATING`** and calls **`WorkflowCompensationService.resumeStuckCompensation`**.

#### Persistence

- **Schema** (`orchestrator/src/main/resources/db/migration/`): **`V1__workflows_and_tasks.sql`** creates `workflows` and `tasks` (composite PK, FK, `dependencies_json`); **`V2__task_compensation_payload.sql`** adds compensation payload column.
- **Entities**: `WorkflowEntity`, `TaskEntity` + **`TaskEntityId`**, **`TaskDependenciesJsonConverter`** for dependency lists.
- **Repositories**: `WorkflowJpaRepository` (includes status transition helpers), `TaskJpaRepository`.
- **Dispatch updates**: **`WorkerDispatchPersistence`** (`REQUIRES_NEW`) — mark success, record failure with exponential backoff, mark `FAILED` when exceeding `orchestration.dispatch.max-retries`.
- **Compensation state**: **`WorkflowCompensationPersistence`** (`REQUIRES_NEW`) — transition to `COMPENSATING`, build compensation plan, mark tasks compensated / compensation failed, finalize workflow after compensation.

#### gRPC client path

- **`OrchestratorGrpcClientConfiguration`**: builds **`ManagedChannel`** to `orchestration.worker.host` / `orchestration.worker.grpc-port` and a blocking **`WorkerServiceGrpc.WorkerServiceBlockingStub`**.
- **`ResilientWorkerGrpcClient`**: wraps **`executeTask`** and **`compensateTask`** with **`@CircuitBreaker(name = "worker")`** (settings in `application.yml` under Resilience4j).
- **`GrpcWorkerTaskDispatcher`**: implements **`WorkerTaskDispatcher`** — builds **`TaskRequest`**, calls execute RPC in a retry loop, persists outcomes via **`WorkerDispatchPersistence`**; on terminal failure invokes **`WorkflowCompensationAsyncRunner`**. Records **`OrchestrationMetrics`**.

#### Compensation (saga-style)

- **`SagaCompensationOrder.reverseTopologicalSuccessOrder`**: orders **successful** tasks for compensation (reverse topological order over the success subgraph).
- **`WorkflowCompensationService`**: drives gRPC **`compensateTask`** with persistence updates and metrics; used from **`WorkflowCompensationAsyncRunner`** (`@Async("compensationExecutor")`) and from recovery scheduler.
- **`CompensationAsyncConfig`**: **`@EnableAsync`** and **`compensationExecutor`** thread pool.

#### Observability

- **`OrchestrationMetrics`**: Micrometer timers `orchestration.task.execution` and `orchestration.compensation.execution` (tagged `outcome`), plus counters for retries/successes/terminal failures as used by dispatcher and compensation service.
- **`application.yml`**: histogram config for those meters; actuator exposes **`health`**, **`info`**, **`prometheus`**.

### Docker and Prometheus

- **`docker-compose.yml`**: **Postgres 16** (`5432`), **Prometheus** (`9099`→`9090` in container); DB name/user/password align with orchestrator defaults; Prometheus mounts `docker/prometheus/prometheus.yml` and uses `host.docker.internal` for scraping.
- **`docker/prometheus/prometheus.yml`**: scrapes `host.docker.internal:8080` at `/actuator/prometheus` (orchestrator on host default port).

### Tests (orchestrator)

Under `orchestrator/src/test/java/...`: **`WorkflowControllerTest`**, **`WorkflowDagValidatorTest`**, **`RunnableTaskSelectorTest`**, **`SagaCompensationOrderTest`**.

## Code references

| Path | What |
|------|------|
| `orchestrator/.../WorkflowController.java` | `POST /workflows` entry |
| `orchestrator/.../WorkflowSubmissionService.java` | Submit, validate, persist, event |
| `orchestrator/.../WorkflowExecutionService.java` | Load graph, select runnable, dispatch |
| `orchestrator/.../GrpcWorkerTaskDispatcher.java` | gRPC execute + persistence + compensation trigger |
| `orchestrator/.../ResilientWorkerGrpcClient.java` | Circuit-broken stubs |
| `orchestrator/.../WorkflowCompensationService.java` | Compensation RPC loop |
| `orchestrator/.../SagaCompensationOrder.java` | Ordering of compensation |
| `orchestrator/.../RunnableTaskSelector.java` | Dependency-based runnable tasks |
| `orchestrator/.../WorkflowDagValidator.java` | DAG validation |
| `orchestrator/.../observability/OrchestrationMetrics.java` | Micrometer registration |
| `orchestrator/src/main/resources/db/migration/*.sql` | Schema |
| `worker/.../WorkerGrpcService.java` | gRPC service implementation |
| `common/src/main/proto/worker.proto` | Contract |
| `spec.md` | Design/spec narrative |

## Architecture (as implemented)

- **Control plane**: Orchestrator owns workflow lifecycle in the database; execution is **pull/select** from current DB state + **push** task RPCs to workers (not a message queue).
- **Data plane**: Workers are stateless RPC servers; business behavior in this repo is **placeholder** in `DefaultTaskExecutor`.
- **Failure model**: Dispatch retries with backoff; open circuit and RPC errors treated as failures; terminal path starts **async compensation** ordered by **`SagaCompensationOrder`**; **scheduled recovery** revisits `COMPENSATING` workflows.
- **Idempotency / at-least-once**: Composite task identity `(workflow_id, task_id)` in DB; dispatcher and persistence paths encode retry and terminal semantics (see `WorkerDispatchPersistence` and task status enums in `common`).

## Historical context (`thoughts/`)

No `thoughts/` directory is present in this working copy, so no supplementary notes were merged into this report.

## Related research

None in-repo under `thoughts/shared/research/`.

## Open questions

- Which gRPC server lifecycle bean is intended to be active when both `GrpcServerLifecycle` and `WorkerGrpcServerLifecycle` are on the classpath (Spring may start duplicate servers or one may be excluded by configuration not captured here).
- OpenTelemetry tracing mentioned in `spec.md` was not traced through `build.gradle.kts` / code in this pass; only metrics-related observability was verified in orchestrator configuration.

## GitHub permalinks (selected)

Base: `https://github.com/ks22ss/DistributedOrchestration/blob/d1ec6321d34065ae8adcea5c6fe1a5a776735210`

- [WorkflowController.java](https://github.com/ks22ss/DistributedOrchestration/blob/d1ec6321d34065ae8adcea5c6fe1a5a776735210/orchestrator/src/main/java/org/example/distributedorchestration/orchestrator/api/WorkflowController.java)
- [worker.proto](https://github.com/ks22ss/DistributedOrchestration/blob/d1ec6321d34065ae8adcea5c6fe1a5a776735210/common/src/main/proto/worker.proto)
- [V1__workflows_and_tasks.sql](https://github.com/ks22ss/DistributedOrchestration/blob/d1ec6321d34065ae8adcea5c6fe1a5a776735210/orchestrator/src/main/resources/db/migration/V1__workflows_and_tasks.sql)

---

*Research used parallel exploration of orchestrator, worker/common, and docker/Gradle configuration. The repo had no `hack/spec_metadata.sh` or `thoughts/` tree; metadata was taken from `git` and the filesystem at research time.*
