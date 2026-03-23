Here’s a **production-grade `spec.md`** you can hand to a coding agent (or use yourself). It’s structured to be **implementation-first**, while still embedding the important system design decisions.

---

# 📄 spec.md — Distributed Task Orchestration System

## 🧾 Overview

Build a **Distributed Workflow Orchestrator** in Java that executes **DAG-based tasks** across distributed workers with:

* Fault tolerance (retry, idempotency, compensation)
* gRPC-based communication
* Observability (metrics + tracing)
* Kubernetes-ready deployment

---

## 🎯 Goals

* Execute workflows defined as **Directed Acyclic Graphs (DAG)**
* Support **parallel execution**
* Guarantee **at-least-once execution with idempotency**
* Provide **failure recovery (retry + compensation)**
* Be **horizontally scalable**

---

## 🧱 System Components

### 1. Orchestrator Service

* Accept workflows
* Manage execution state
* Dispatch tasks to workers
* Handle retries and compensation

### 2. Worker Service

* Execute tasks
* Return result (success/failure)

### 3. Persistence Layer

* Store workflow + task state

### 4. Communication Layer

* gRPC between orchestrator and workers

### 5. Observability

* Metrics (Micrometer)
* Tracing (OpenTelemetry)

---

## 🧠 Core Design Principles

### DAG Execution

* Tasks execute only when dependencies are satisfied

### Idempotency

* Same task execution should not produce duplicate side effects

### Retry with Backoff

* Exponential retry strategy

### Compensation (Saga Pattern)

* Each task may define a rollback action

---

# 🗂️ Project Structure

```id="l9s6rj"
orchestrator/
  ├── api/
  ├── service/
  ├── scheduler/
  ├── repository/
  ├── model/

worker/
  ├── grpc/
  ├── executor/

common/
  ├── proto/
  ├── model/
```

---

# 🧩 Step-by-Step Implementation Guide

---

## ✅ Step 1 — Setup Project

### Tech Stack

* Java 21
* Spring Boot
* gRPC (grpc-java)
* PostgreSQL
* Resilience4j
* Micrometer + Prometheus
* OpenTelemetry

---

## ✅ Step 2 — Define Core Models

### Task

```java id="d7e91h"
class Task {
    String taskId;
    String workflowId;
    List<String> dependencies;
    TaskStatus status;
    int retryCount;
    String payload;
}
```

---

### Workflow

```java id="xxsflr"
class Workflow {
    String workflowId;
    Map<String, Task> tasks;
    WorkflowStatus status;
}
```

---

## ✅ Step 3 — Database Schema

### workflow table

```sql id="g1ehsz"
CREATE TABLE workflows (
    workflow_id VARCHAR PRIMARY KEY,
    status VARCHAR,
    created_at TIMESTAMP
);
```

---

### task table

```sql id="mpg7xv"
CREATE TABLE tasks (
    task_id VARCHAR,
    workflow_id VARCHAR,
    status VARCHAR,
    retry_count INT,
    payload TEXT,
    PRIMARY KEY (task_id, workflow_id)
);
```

---

## 🧠 Design Decision: DB is Source of Truth

* Required for crash recovery
* Enables idempotency tracking

---

## ✅ Step 4 — DAG Validation

### Implement cycle detection (DFS)

```java id="c1o6dq"
boolean hasCycle(Map<String, Task> tasks) {
    // standard DFS cycle detection
}
```

---

## 🧠 Design Decision: Validate Early

Reject invalid workflows before execution

---

## ✅ Step 5 — Orchestrator API

### Submit Workflow

```http id="3qahx3"
POST /workflows
```

* Validate DAG
* Persist workflow + tasks
* Trigger execution

---

## ✅ Step 6 — Scheduler Logic

### Find Runnable Tasks

```java id="nf9r4c"
List<Task> findRunnableTasks(Workflow wf) {
    return wf.tasks.values().stream()
        .filter(t -> t.status == PENDING)
        .filter(t -> dependenciesCompleted(t))
        .toList();
}
```

---

## 🧠 Design Decision: Pull-based scheduling inside orchestrator

* Simpler control flow
* Can evolve to queue-based later

---

## ✅ Step 7 — gRPC Communication

### Define proto

```proto id="y6yq8d"
service WorkerService {
  rpc ExecuteTask(TaskRequest) returns (TaskResponse);
}
```

---

### TaskRequest

```proto id="0x86x9"
message TaskRequest {
  string taskId = 1;
  string payload = 2;
}
```

---

## 🧠 Design Decision: gRPC over REST

* Lower latency
* Strong typing
* Better for internal systems

---

## ✅ Step 8 — Worker Implementation

```java id="p0r7r6"
public class WorkerServiceImpl extends WorkerServiceGrpc.WorkerServiceImplBase {

    @Override
    public void executeTask(TaskRequest request,
                           StreamObserver<TaskResponse> responseObserver) {
        try {
            // Execute business logic
            responseObserver.onNext(successResponse());
        } catch (Exception e) {
            responseObserver.onNext(failureResponse());
        }
        responseObserver.onCompleted();
    }
}
```

---

## 🧠 Design Decision: Stateless Workers

* Easy horizontal scaling
* Safe restarts

---

## ✅ Step 9 — Retry Mechanism

```java id="y68j9k"
int delay = (int) Math.pow(2, retryCount);
Thread.sleep(delay * 1000);
```

---

## 🧠 Design Decision: Exponential Backoff

* Prevents system overload
* Industry standard

---

## ✅ Step 10 — Idempotency

### Strategy

* Use `(workflowId, taskId)` as unique key
* Before execution:

  * Check if already SUCCESS → skip

---

## 🧠 Design Decision: At-least-once + Idempotency

* Easier than exactly-once
* More reliable

---

## ✅ Step 11 — Compensation Logic

### Extend Task

```java id="r5p8kw"
interface TaskExecutor {
    void execute();
    void compensate();
}
```

---

## 🧠 Design Decision: Saga Pattern

* Enables rollback across distributed systems

---

## ✅ Step 12 — Circuit Breaker

```java id="2q5k9z"
CircuitBreaker cb = CircuitBreaker.ofDefaults("worker");
```

Wrap gRPC calls with Resilience4j.

---

## 🧠 Design Decision: Prevent Cascading Failures

---

## ✅ Step 13 — Observability

### Metrics

* task_execution_time
* retry_count
* failure_rate

### Tracing

* Trace each workflow execution

---

## 🧠 Design Decision: Observability is mandatory

Not optional in distributed systems

---

## ✅ Step 14 — Kubernetes Deployment

### Orchestrator Deployment

* 2 replicas

### Worker Deployment

* HPA enabled

---

### HPA Example

```yaml id="9q2azv"
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
```

---

## 🧠 Design Decision: Scale on Queue Depth

* Better than CPU

---

## ✅ Step 15 — Graceful Shutdown

* Stop accepting new tasks
* Finish current tasks
* Persist state

---

## 🧠 Design Decision: Prevent Task Loss

---

# 🧪 Testing Strategy

### Unit Tests

* DAG validation
* Scheduler logic

### Integration Tests

* Orchestrator ↔ Worker (gRPC)

### Failure Tests

* Simulate worker crash
* Retry correctness

---

# 🚀 Future Enhancements

* Replace gRPC push with Kafka queue
* Add workflow versioning
* Add UI dashboard
* Priority scheduling
* Multi-region deployment

---

# 🏁 Definition of Done

* Workflow executes correctly with DAG dependencies
* Tasks retry correctly on failure
* Idempotency prevents duplicate execution
* Compensation runs on failure
* Metrics and traces are visible
* System scales in Kubernetes

---