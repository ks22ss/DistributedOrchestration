# Final thoughts (scope and design)

What this project is meant to be, what I’m deliberately *not* building, and why a few choices look the way they do.

## What this project is

A **small, readable distributed orchestration sample**: one **orchestrator** (REST, Postgres, scheduling logic) talks to one or more **workers** over **gRPC**. Workflows are **DAGs** of tasks with **retries**, **saga-style compensation** when retries are exhausted, and **basic observability** (Actuator/Prometheus, optional OTLP traces via the Java agent). Infra in Docker covers Postgres, Prometheus, and Jaeger so I can run it on a laptop without pretending it’s already a production platform.

## In scope (what I expect this repo to do)

- **Central orchestration**: the database is the source of truth for workflow and task state; the orchestrator decides what is runnable and pushes work to workers.
- **DAG rules**: validate the graph (including cycles), run tasks only when dependencies have **succeeded**, advance the graph after each success.
- **At-least-once style execution with backoff**: failed attempts are recorded; retry timing is driven by **`next_retry_at`** and a **scheduler**, not by blocking HTTP threads on `Thread.sleep`.
- **Concurrency where it’s cheap**: independent runnable tasks in the same “wave” can dispatch on a **bounded thread pool**; pool size and queue cap are config.
- **Horizontal scale of workers (simple form)**: **comma-separated endpoints** and a **round-robin stub pool**—no discovery, no health-aware routing, but multiple processes on different host:port values work.
- **Resilience4j circuit breaker** on the worker client (one logical breaker around the pooled stubs—good enough for a demo, not per-backend isolation).
- **Compensation path** and a **recovery scheduler** for stuck compensating workflows.
- **Clear boundaries** in the orchestrator (events, transactions, gRPC client, persistence) so the code stays teachable.

## Out of scope (what I am not implementing)

I’m **not** pursuing the heavier features, such as: **message buses** (Kafka, RabbitMQ, etc.) as the primary dispatch path, **Envoy / xDS / advanced gRPC load balancing**, **Kubernetes manifests and HPA**, **multi-region**, a **workflow UI**, **workflow versioning**, **formal exactly-once** semantics, **outbox/inbox** hardening for publish–persist alignment, **per-worker circuit breakers**, or **deep idempotency guarantees** inside the sample worker (the worker stays a stubby illustration).

This repo is a **learning and experimentation spine**, not a product I intend to operate at scale. If I needed those things, I’d reach for a real orchestration engine (Temporal, Camunda, Step Functions, etc.) or invest in a much larger codebase.

## Reflections on a few design choices

**Transactional events**  
I publish **`WorkflowSubmittedEvent`** inside the submit transaction but handle it with **`@TransactionalEventListener(AFTER_COMMIT)`** so execution only starts after the workflow and tasks are actually committed. That avoids “start running something that might roll back.” **`TaskCompletedEvent`** is different: it’s fired from a **`REQUIRES_NEW`** transaction’s **`afterCommit`** hook, where there is no surrounding Spring transaction, so a plain **`@EventListener`** is the reliable choice. That split is easy to miss and worth the comment in code.

**`REQUIRES_NEW` on dispatch persistence**  
Short transactions for success/failure updates keep DB connections from being held across gRPC calls and backoff windows. Same idea as moving backoff off the thread: **don’t tie resource lifetime to slow or flaky network**.

**`next_retry_at` as both lease and backoff**  
I avoided a long-lived **`RUNNING`** status that could strand tasks after a crash. A **time-based lease** on **`next_retry_at`** keeps the runnable selector from double-dispatching while a call is in flight, and if the JVM dies, the lease eventually expires and a retry can be attempted again. Duplicate delivery remains possible; **idempotent workers** would be required in a real system.

**Parallel dispatch**  
The DAG already enforces **ordering where it matters** (dependencies). Parallelism is only across tasks that are simultaneously runnable; I bounded it with a fixed pool and **`CallerRunsPolicy`** so overload shows up as back-pressure instead of unbounded queue growth.

**Multiple workers via round-robin**  
It’s the smallest step past a single host: **no discovery**, **no stickiness**, **no per-node metrics in the client**. The tradeoff is explicit: one shared circuit breaker and blind rotation are acceptable here; they wouldn’t be for a hard SLA without more work.

## Closing

I’m happy with this as a **coherent slice** of distributed systems concerns—transactions, events, gRPC, retries, sagas, metrics—without boiling the ocean. Anything that smells like “platform” or “product” is intentionally left out unless I pick it up again with a different goal in mind.
