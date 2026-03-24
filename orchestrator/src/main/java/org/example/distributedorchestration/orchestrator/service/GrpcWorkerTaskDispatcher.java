package org.example.distributedorchestration.orchestrator.service;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.springframework.stereotype.Service;

/**
 * Dispatches runnable tasks to workers via gRPC with exponential backoff between retries (spec Step 9).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GrpcWorkerTaskDispatcher implements WorkerTaskDispatcher {

    private final WorkerServiceGrpc.WorkerServiceBlockingStub workerStub;
    private final WorkerDispatchPersistence persistence;

    @Override
    public void dispatch(Task task) {
        TaskEntityId id = new TaskEntityId(task.getTaskId(), task.getWorkflowId());
        TaskRequest request = TaskRequest.newBuilder()
                .setTaskId(task.getTaskId())
                .setPayload(task.getPayload() == null ? "" : task.getPayload())
                .build();

        while (true) {
            try {
                var response = workerStub.executeTask(request);
                if (response.getSuccess()) {
                    persistence.markSuccess(id);
                    return;
                }
                log.warn(
                        "Worker reported failure workflowId={} taskId={} message={}",
                        task.getWorkflowId(),
                        task.getTaskId(),
                        response.getMessage());
                if (handleFailure(id, task)) {
                    return;
                }
            } catch (StatusRuntimeException e) {
                log.error(
                        "gRPC dispatch failed workflowId={} taskId={}",
                        task.getWorkflowId(),
                        task.getTaskId(),
                        e);
                if (handleFailure(id, task)) {
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
            return true;
        }
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
