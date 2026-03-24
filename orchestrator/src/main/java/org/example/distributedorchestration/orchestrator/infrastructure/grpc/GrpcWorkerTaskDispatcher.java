package org.example.distributedorchestration.orchestrator.infrastructure.grpc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.orchestrator.application.event.TaskCompletedEvent;
import org.example.distributedorchestration.orchestrator.application.port.WorkerTaskDispatcher;
import org.example.distributedorchestration.orchestrator.application.service.WorkflowCompensationAsyncRunner;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.orchestrator.infrastructure.persistence.dispatch.WorkerDispatchPersistence;
import org.example.distributedorchestration.orchestrator.observability.OrchestrationMetrics;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Dispatches runnable tasks to workers via gRPC with exponential backoff (Step 9), circuit breaker (Step 12),
 * and metrics (Step 13).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrpcWorkerTaskDispatcher implements WorkerTaskDispatcher {

    private final ResilientWorkerGrpcClient resilientWorkerClient;
    private final WorkerDispatchPersistence persistence;
    private final WorkflowCompensationAsyncRunner workflowCompensationAsyncRunner;
    private final OrchestrationMetrics orchestrationMetrics;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void dispatch(Task task) {
        log.debug("Dispatch start workflowId={} taskId={}", task.getWorkflowId(), task.getTaskId());
        dispatchBody(task);
    }

    private void dispatchBody(Task task) {
        TaskEntityId id = new TaskEntityId(task.getTaskId(), task.getWorkflowId());
        TaskRequest request = TaskRequest.newBuilder()
                .setTaskId(task.getTaskId())
                .setPayload(task.getPayload() == null ? "" : task.getPayload())
                .build();

        while (true) {
            long t0 = System.nanoTime();
            try {
                var response = resilientWorkerClient.executeTask(request);
                orchestrationMetrics.recordTaskExecutionTime(
                        Duration.ofNanos(System.nanoTime() - t0),
                        response.getSuccess() ? "success" : "worker_failure");
                if (response.getSuccess()) {
                    persistence.markSuccess(id);
                    orchestrationMetrics.recordDispatchSuccess();
                    log.info("Dispatch success workflowId={} taskId={}", task.getWorkflowId(), task.getTaskId());
                    eventPublisher.publishEvent(new TaskCompletedEvent(task.getWorkflowId(), task.getTaskId()));
                    return;
                }
                log.warn(
                        "Worker reported failure workflowId={} taskId={} message={}",
                        task.getWorkflowId(),
                        task.getTaskId(),
                        response.getMessage());
                if (handleFailure(id, task)) {
                    orchestrationMetrics.recordDispatchTerminalFailure();
                    return;
                }
            } catch (CallNotPermittedException e) {
                orchestrationMetrics.recordTaskExecutionTime(
                        Duration.ofNanos(System.nanoTime() - t0), "circuit_open");
                log.warn(
                        "Worker circuit breaker open workflowId={} taskId={}",
                        task.getWorkflowId(),
                        task.getTaskId());
                if (handleFailure(id, task)) {
                    orchestrationMetrics.recordDispatchTerminalFailure();
                    return;
                }
            } catch (StatusRuntimeException e) {
                orchestrationMetrics.recordTaskExecutionTime(
                        Duration.ofNanos(System.nanoTime() - t0), "rpc_error");
                log.error(
                        "gRPC dispatch failed workflowId={} taskId={}",
                        task.getWorkflowId(),
                        task.getTaskId(),
                        e);
                if (handleFailure(id, task)) {
                    orchestrationMetrics.recordDispatchTerminalFailure();
                    return;
                }
            }
        }
    }

    private boolean handleFailure(TaskEntityId id, Task task) {
        WorkerDispatchPersistence.BackoffOutcome outcome = persistence.recordFailureAndGetBackoff(id);
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
                "Dispatch retry scheduled workflowId={} taskId={} delaySeconds={}",
                task.getWorkflowId(),
                task.getTaskId(),
                outcome.delaySeconds());
        sleepBackoffSeconds(outcome.delaySeconds());
        return false;
    }

    /** Spec Step 9: {@code Thread.sleep(delay * 1000)}. */
    private static void sleepBackoffSeconds(int delaySeconds) {
        try {
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("dispatch interrupted during backoff", e);
        }
    }
}
