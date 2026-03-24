package org.example.distributedorchestration.orchestrator.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.orchestrator.grpc.ResilientWorkerGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowCompensationService {

    private final WorkflowCompensationPersistence compensationPersistence;
    private final ResilientWorkerGrpcClient resilientWorkerClient;

    @Value("${orchestration.compensation.max-retries:5}")
    private int maxRetries;

    public void compensateAfterTerminalTaskFailure(String workflowId) {
        runCompensation(workflowId, false);
    }

    /** Scheduler: resume after crash mid-saga (workflow already {@code COMPENSATING}). */
    public void resumeStuckCompensation(String workflowId) {
        runCompensation(workflowId, true);
    }

    private void runCompensation(String workflowId, boolean recovery) {
        CompensationStartResult start =
                compensationPersistence.tryStartOrResumeCompensation(workflowId, recovery);
        if (start.type() == CompensationStartResult.Type.SKIP
                || start.type() == CompensationStartResult.Type.NO_SUCCESS_TASKS) {
            return;
        }
        for (CompensationItem item : start.items()) {
            compensateWithRetries(item);
        }
        compensationPersistence.finishWorkflowAfterCompensation(workflowId);
    }

    private void compensateWithRetries(CompensationItem item) {
        CompensationRequest request = CompensationRequest.newBuilder()
                .setTaskId(item.taskId().getTaskId())
                .setWorkflowId(item.workflowId())
                .setCompensationPayload(item.compensationPayload() == null ? "" : item.compensationPayload())
                .build();

        int attempt = 0;
        while (true) {
            try {
                var response = resilientWorkerClient.compensateTask(request);
                if (response.getSuccess()) {
                    compensationPersistence.markCompensated(item.taskId());
                    return;
                }
                log.warn(
                        "Compensation RPC logical failure workflowId={} taskId={} message={}",
                        item.workflowId(),
                        item.taskId().getTaskId(),
                        response.getMessage());
            } catch (CallNotPermittedException e) {
                log.warn(
                        "Worker circuit breaker open during compensation workflowId={} taskId={}",
                        item.workflowId(),
                        item.taskId().getTaskId());
            } catch (StatusRuntimeException e) {
                log.error(
                        "Compensation gRPC error workflowId={} taskId={}",
                        item.workflowId(),
                        item.taskId().getTaskId(),
                        e);
            }
            attempt++;
            if (attempt > maxRetries) {
                log.error(
                        "Compensation retries exhausted workflowId={} taskId={}",
                        item.workflowId(),
                        item.taskId().getTaskId());
                compensationPersistence.markCompensationFailed(item.taskId());
                return;
            }
            int delaySeconds = (int) Math.pow(2, attempt - 1);
            sleepSeconds(delaySeconds);
        }
    }

    private static void sleepSeconds(int delaySeconds) {
        try {
            Thread.sleep(delaySeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("compensation backoff interrupted", e);
        }
    }
}
