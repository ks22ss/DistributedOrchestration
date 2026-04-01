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

| Service    | Purpose | On my machine |
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

## Thoughts on the project 

This project is a sample distributed DAG workflow orchestrator in Java. It is a Spring Boot application that uses a PostgreSQL database to store workflow and task data. It also uses a gRPC client to dispatch tasks to a worker. The orchestrator is responsible for validating the workflow DAG and dispatching tasks to the worker. The worker is responsible for executing the tasks and updating the workflow and task data in the database.

**Here are some of my thoughts and observations during development process**


## Hypothesis and Thoughts come up:


### 1. Entity Design

**Why `TaskEntity` uses `@EmbeddedId` (`TaskEntityId`: `task_id` + `workflow_id` as strings) instead of `@MapsId` on a `@ManyToOne` to `WorkflowEntity`**

JPA often shows a pattern like: task has a `@ManyToOne` workflow, and the embeddable id’s `workflowId` is **`@MapsId`** so the primary key’s foreign-key column is **shared** with the association—one conceptual “source” for `workflow_id` on the row.

I did **not** take that route here, on purpose:

- **Simpler entity lifecycle** — With `@MapsId`, creating a `TaskEntity` usually means wiring a **managed** `WorkflowEntity` (or carefully setting both the relation and the id fields so they stay consistent). For bulk insert of tasks right after `WorkflowEntity` is saved, an **`EmbeddedId` with two strings** is just: `new TaskEntityId(taskId, workflowId)` — no requirement to navigate the object graph for the PK to be correct.

- **Clear separation of concerns** — `TaskEntity` stays a flat row mapping: id embeddable + columns. A `@ManyToOne` would pull **navigation and fetch semantics** (lazy vs eager, N+1 questions) into an entity that I mostly treat as **state + columns** behind repositories. I can still enforce **`workflow_id` → `workflows.workflow_id`** with a **database foreign key** in migrations without teaching Hibernate that the PK is “derived from” the parent entity.

I do **not** get JPA-managed “FK and PK share one field” for free; I must keep `workflow_id` in `TaskEntityId` **in sync** with business rules myself (the code always constructs the id from the same `workflowId` when saving tasks). For this sample, that is a small, explicit cost. `@EmbeddedId` with string-only components is a deliberate choice for **readability and straightforward persistence** of composite natural keys. **`@MapsId`** would be a good fit if I wanted the task’s PK to be **formally tied** to a `WorkflowEntity` association in the ORM layer; I preferred a **minimal** mapping and rely on the schema + application code for consistency.

### 2. Spring Boot Transaction

In the path of workflow submission, 
1. the workflow is saved to the database
2. the tasks are saved to the database
3. then a WorkflowSubmittedEvent is published

```java

   // In the WorkflowSubmissionService class
    @Transactional
    @Override
    public SubmitWorkflowResult submit(SubmitWorkflowCommand command) {
        //...
        workflowRepository.save(new WorkflowEntity(workflowId, WorkflowStatus.RUNNING, createdAt));

        for (SubmitWorkflowTaskCommand taskCmd : command.tasks()) {
            //...
            taskRepository.save(entity);
        }
        //...

        eventPublisher.publishEvent(new WorkflowSubmittedEvent(workflowId));
        //...
        return new SubmitWorkflowResult(workflowId, WorkflowStatus.RUNNING.name());
    }


    //...
   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
   public void onWorkflowSubmitted(WorkflowSubmittedEvent event) {
      log.info("Workflow submitted event received workflowId={}", event.workflowId());
      workflowExecutionService.triggerExecution(event.workflowId());
   }

   //
    @Transactional(readOnly = true)
    @Override
    public void triggerExecution(String workflowId) {
        log.info("Trigger execution start workflowId={}", workflowId);
        //...
    }
```

1) What if triggerExecution is a long process, no distributed workers are involved?

The flow of the workflow submission is: 
-> workflow submission 
-> save workflow and tasks 
-> publish WorkflowSubmittedEvent 
-> triggerExecution (long process)

User will got delay response while waiting for the execution to finish trigger.
Because in my case tiggerExecution is a fast dispatch process, it will not block the HTTP thread that long. So it is fine leave it sequential.
But if it is a long process, it will block the HTTP thread and held user's thread blocked. User preceive higher latency.

To solve this, use `@Async` annotation.

Enable async processing (`@EnableAsync` on a configuration class) and provide a `TaskExecutor` (or rely on Spring’s default). Then mark `@Async` to either the execution entry point or the listener so the heavy work runs on a worker thread instead of the HTTP thread.

```java
@Async
@Transactional(readOnly = true)
@Override
public void triggerExecution(String workflowId) {
   log.info("Trigger execution start workflowId={}", workflowId);
   // ...
}
```

But we may still hit resources limit if the demand exceed thread pool capacity, doesn't matter if using the default pool or you create custom-tuned pool. That's why distributed workers are needed in case. Spring runs tasks on a 


2) Read-Write Split database

I was wonder what if I am not using a single Database, but a Read Write Master Slaves style Database Cluster. 
The `AFTER_COMMIT` fires the moment the Master Wrtie DB says "OK." If the Slave DB hasn't replicated that new Workflow yet, and the execution service tries to read the workflow from the Slave, may throw an `EntityNotFoundException`.
A tiny delay / retry can solve this.

3) "Ghost" Event

Let's say the Transaction commits successfully in the Database.
But the Server immediately crashes before it can execute the `AFTER_COMMIT` logic.
Now `triggerExecution()` was never called.
Workflow will forever stuck running.

Same thing if anything goes wrong with the `triggerExecution()` call. workers never hear about the dispatched workflow.

**OutBox Pattern** can solve this. Instead of publishing a volatile event, save a WorkflowJob entity in the same transaction. A background worker reads the outbox table then picks up any `Pending` jobs that haven't started. This is up to the requirement, is this task or workflow really a mission critical event?



4) Over Catching Exceptions

I was imagining the controller is like this:
```java
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitWorkflowResult submit(@Valid @RequestBody SubmitWorkflowRequest request) {

         try {
            SubmitWorkflowResult result = workflowSubmissionService.submit(toCommand(request));
         } catch (Exception e) {
             // Swallowing the exception
             // The user/caller gets no indication that the DB actually rolled back.
         }

         // return to user with 200
```

That's why using have `Global Exception Handler` allow the Spring Transaction Proxy to see the error, perform a full rollback, and ensure my AFTER_COMMIT listener never fires.

Using the global adviser pattern, we can catch all exceptions and perform a full rollback + custom user friendly response.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateWorkflowException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateWorkflowException ex) {
        // Return 409 Conflict, Client Knows what went wrong
        return ResponseEntity.status(HttpStatus.CONFLICT)
                             .body(new ErrorResponse("ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDbError(DataIntegrityViolationException ex) {
        // Return 400 Bad Request for constraint violations, Client Knows what went wrong
        return ResponseEntity.badRequest()
                             .body(new ErrorResponse("DATABASE_ERROR", "Invalid data provided"));
    }
}
```

The only tradeoff is we need to know and map the exceptions ahead of time or use a Safty Net Pattern fallback to 500.


5) Network calls, gRPC clients fail

Another ghost event scenario gRPC worker starts slowing down (Partial Failure). If no Circuit Breaker,  submit thread at the AFTER_COMMIT listener will hang. stuck waiting for gRPC timeouts, and entire application will stop responding. Database says the workflow is `RUNNING` but no task was actually dispatched.

A `Circuit Breaker` comes into play here. Instead of hanging, circult breaker make it fail fast. The listener after circult breaks throws a `CallNotPermittedException` **immediately without even trying the network until the open wait time elapses**. But still we need to handle the failure and update the Workflow status in the DB to FAILED_TO_START or QUEUED_FOR_RETRY. And handle that later like, retry + apply compensation SAGA pattern.

In this project, we use `Resilience4j` to implement the Circuit Breaker.

The `AFTER_COMMIT` listener does not do the gRPC call.
It calls `triggerExecution()`, which only selects runnable tasks and submits dispatch jobs to `dispatchExecutor` that are running on a thread pool. The gRPC call happens later on those executor threads, not on the original HTTP thread. When the circuit is OPEN, dispatch fails fast and is persisted as “retry later.

```java
// GrpcWorkerTaskDispatcher
    private boolean handleFailure(TaskEntityId id, Task task, Instant now) {
        WorkerDispatchPersistence.BackoffOutcome outcome = persistence.recordFailureAndScheduleRetry(id, now);
        if (outcome.exhausted()) {
            log.warn(
                    "Retries exhausted for workflowId={} taskId={}",
                    task.getWorkflowId(),
                    task.getTaskId());
            workflowCompensationAsyncRunner.triggerCompensation(task.getWorkflowId());
            return true;
        }
```

```java
// WorkerDispatchPersistence.java
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackoffOutcome recordFailureAndScheduleRetry(TaskEntityId id, Instant now) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        int delaySeconds = (int) Math.pow(2, entity.getRetryCount());
        entity.setRetryCount(entity.getRetryCount() + 1);
        if (entity.getRetryCount() > maxRetries) {
            entity.setStatus(TaskStatus.FAILED);
            entity.setNextRetryAt(null);
            taskRepository.save(entity);
            return new BackoffOutcome(0, true);
        }
        entity.setStatus(TaskStatus.PENDING);
        entity.setNextRetryAt(now.plusSeconds(delaySeconds));
        taskRepository.save(entity);
        return new BackoffOutcome(delaySeconds, false);
    }
```


By labeling the status as `PENDING`, the retry scanner will pick it up and try again.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DispatchRetryScheduler {

    private final TaskJpaRepository taskRepository;
    private final WorkflowExecutionUseCase workflowExecutionService;

    @Scheduled(fixedDelayString = "${orchestration.dispatch.retry-scan-interval-ms:1000}")
    public void dispatchDueRetries() {
        Instant now = Instant.now();
        for (String workflowId :
                taskRepository.findDistinctWorkflowIdsWithRetriesDue(TaskStatus.PENDING, now)) {
            log.debug("Retry scan: triggering execution workflowId={}", workflowId);
            try {
                workflowExecutionService.triggerExecution(workflowId);
            } catch (RuntimeException e) {
                log.warn("Retry scan failed workflowId={}", workflowId, e);
            }
        }
    }
}
```