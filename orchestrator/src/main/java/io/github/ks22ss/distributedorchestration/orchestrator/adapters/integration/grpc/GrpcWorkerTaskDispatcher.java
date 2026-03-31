package io.github.ks22ss.distributedorchestration.orchestrator.adapters.integration.grpc;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.dispatch.WorkerDispatchPersistence;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.observability.OrchestrationMetrics;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.out.WorkerTaskDispatcher;
import io.github.ks22ss.distributedorchestration.orchestrator.application.usecase.WorkflowCompensationAsyncRunner;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.springframework.stereotype.Service;

/**
 * Dispatches runnable tasks to workers via gRPC with exponential backoff persisted as {@code next_retry_at}, circuit
 * breaker, and metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrpcWorkerTaskDispatcher implements WorkerTaskDispatcher {

    private final ResilientWorkerGrpcClient resilientWorkerClient;
    private final WorkerDispatchPersistence persistence;
    private final WorkflowCompensationAsyncRunner workflowCompensationAsyncRunner;
    private final OrchestrationMetrics orchestrationMetrics;

    @Override
    public void dispatch(Task task) {
        log.debug("Dispatch start workflowId={} taskId={}", task.getWorkflowId(), task.getTaskId());
        dispatchBody(task);
    }

    private void dispatchBody(Task task) {
        TaskEntityId id = new TaskEntityId(task.getTaskId(), task.getWorkflowId());
        Instant now = Instant.now();
        if (!persistence.tryBeginDispatch(id, now)) {
            log.debug(
                    "Skip dispatch (not claimable) workflowId={} taskId={}",
                    task.getWorkflowId(),
                    task.getTaskId());
            return;
        }

        TaskRequest request =
                TaskRequest.newBuilder()
                        .setTaskId(task.getTaskId())
                        .setPayload(task.getPayload() == null ? "" : task.getPayload())
                        .build();

        long t0 = System.nanoTime();
        try {
            var response = resilientWorkerClient.executeTask(request);
            orchestrationMetrics.recordTaskExecutionTime(
                    Duration.ofNanos(System.nanoTime() - t0),
                    response.getSuccess() ? "success" : "worker_failure");
            if (response.getSuccess()) {
                persistence.markSuccess(id);
                orchestrationMetrics.recordDispatchSuccess();
                log.info(
                        "Dispatch success workflowId={} taskId={}", task.getWorkflowId(), task.getTaskId());
                return;
            }
            log.warn(
                    "Worker reported failure workflowId={} taskId={} message={}",
                    task.getWorkflowId(),
                    task.getTaskId(),
                    response.getMessage());
            if (handleFailure(id, task, Instant.now())) {
                orchestrationMetrics.recordDispatchTerminalFailure();
            }
        } catch (CallNotPermittedException e) {
            orchestrationMetrics.recordTaskExecutionTime(
                    Duration.ofNanos(System.nanoTime() - t0), "circuit_open");
            log.warn(
                    "Worker circuit breaker open workflowId={} taskId={}",
                    task.getWorkflowId(),
                    task.getTaskId());
            if (handleFailure(id, task, Instant.now())) {
                orchestrationMetrics.recordDispatchTerminalFailure();
            }
        } catch (StatusRuntimeException e) {
            orchestrationMetrics.recordTaskExecutionTime(
                    Duration.ofNanos(System.nanoTime() - t0), "rpc_error");
            log.error(
                    "gRPC dispatch failed workflowId={} taskId={}",
                    task.getWorkflowId(),
                    task.getTaskId(),
                    e);
            if (handleFailure(id, task, Instant.now())) {
                orchestrationMetrics.recordDispatchTerminalFailure();
            }
        }
    }

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
        orchestrationMetrics.recordDispatchRetryAttempt();
        log.debug(
                "Dispatch retry deferred workflowId={} taskId={} delaySeconds={}",
                task.getWorkflowId(),
                task.getTaskId(),
                outcome.delaySeconds());
        return false;
    }
}

