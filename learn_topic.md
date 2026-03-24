# Topics worth learning (this project)

Short list of concepts that show up directly in distributed orchestration, Spring, and gRPC code like this repo.

## 1. Spring transactions and boundaries

- **Propagation**: `REQUIRED` vs `REQUIRES_NEW` — why dispatch persistence uses a new transaction so long sleeps and retries do not hold one DB connection.
- **Read-only transactions**: what they optimize and when they still participate in transaction synchronization.
- **Transaction boundaries vs business events**: “success in DB” is not the same as “RPC returned OK” until the commit that persists status completes.

## 2. Spring application events vs transactional events

- **`@EventListener`**: runs when the event is published (synchronously by default unless `@Async`).
- **`@TransactionalEventListener` + `TransactionPhase.AFTER_COMMIT`**: only runs in relation to an **active** Spring transaction; if the event is published with no transaction (or the wrong phase), listeners may **not** run unless `fallbackExecution = true`.
- **Publishing after inner transactions**: when `REQUIRES_NEW` commits, publishing from that transaction’s **`TransactionSynchronization.afterCommit`** is a reliable pattern so dependents see committed state.

## 3. Orchestration vs choreography

- **Orchestrator-driven** (this project): a central component decides what runs next, calls workers, updates workflow/task state.
- **Choreography**: services react to events without a single coordinator; trade-offs for consistency, observability, and coupling.

## 4. DAG scheduling and dependency satisfaction

- **Topological order** and **cycle detection** (why invalid graphs must be rejected before execution).
- **Runnable set**: tasks that are `PENDING` with all dependencies `SUCCESS` — why you must **re-evaluate** runnable tasks after each task completes (not only at workflow submit).

## 5. At-least-once execution and idempotency

- Retries, backoff, and duplicate delivery: workers and side effects should be **idempotent** where possible.
- **Outbox / inbox** patterns (optional deeper dive): making “persist state” and “publish message” consistent across failures.

## 6. gRPC in Java (client and server)

- **Stubs** (blocking vs async), **channels**, deadlines/cancellation, and **status codes** (`StatusRuntimeException`).
- **Proto packages** vs **Java packages** (`java_package`, `java_multiple_files`) and generated code layout.

## 7. Resilience on the call path

- **Circuit breakers** (e.g. Resilience4j): when they open, what `CallNotPermittedException` means, and how that differs from a logical failure in `TaskResponse`.

## 8. Saga-style compensation

- **Forward actions** vs **compensating actions**; ordering compensation (often reverse dependency order over succeeded steps).
- **vs two-phase commit (2PC)**: sagas favor availability and explicit failure modes over strong atomicity across services.

## 9. Observability

- **Structured logging** (workflow id, task id) for tracing one run end-to-end.
- **Metrics** (latency, retries, terminal failures) vs logs — when each is appropriate.

## 10. Optional next steps

- **Process engines** (Temporal, Camunda) vs custom orchestration — when buying a platform pays off.
- **Event sourcing** for workflow state (auditability, replay) — heavier model, strong guarantees when you need them.

---

Pick one lane per week: (A) Spring transactions + events, (B) gRPC + resilience, (C) saga/DAG semantics. Re-read this codebase after each lane and map classes to the concepts above.
