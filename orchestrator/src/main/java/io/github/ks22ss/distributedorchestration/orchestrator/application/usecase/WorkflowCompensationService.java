package io.github.ks22ss.distributedorchestration.orchestrator.application.usecase;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.compensation.WorkflowCompensationPersistence;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.integration.grpc.ResilientWorkerGrpcClient;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.observability.OrchestrationMetrics;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.entities.CompensationItem;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.entities.CompensationStartResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WorkflowCompensationService implements io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.WorkflowCompensationUseCase {

    private final WorkflowCompensationPersistence compensationPersistence;
    private final ResilientWorkerGrpcClient resilientWorkerClient;
    private final OrchestrationMetrics orchestrationMetrics;
    private final int maxRetries;

    public WorkflowCompensationService(
            WorkflowCompensationPersistence compensationPersistence,
            ResilientWorkerGrpcClient resilientWorkerClient,
            OrchestrationMetrics orchestrationMetrics,
            @Value("${orchestration.compensation.max-retries:5}") int maxRetries) {
        this.compensationPersistence = compensationPersistence;
        this.resilientWorkerClient = resilientWorkerClient;
        this.orchestrationMetrics = orchestrationMetrics;
        this.maxRetries = maxRetries;
    }

    @Override
    public void compensateAfterTerminalTaskFailure(String workflowId) {
        runCompensation(workflowId, false);
    }

    /** Scheduler: resume after crash mid-saga (workflow already {@code COMPENSATING}). */
    @Override
    public void resumeStuckCompensation(String workflowId) {
        runCompensation(workflowId, true);
    }

    private void runCompensation(String workflowId, boolean recovery) {
        runCompensationBody(workflowId, recovery);
    }

    private void runCompensationBody(String workflowId, boolean recovery) {
        CompensationStartResult start = compensationPersistence.tryStartOrResumeCompensation(workflowId, recovery);
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
        CompensationRequest request =
                CompensationRequest.newBuilder()
                        .setTaskId(item.taskId().getTaskId())
                        .setWorkflowId(item.workflowId())
                        .setCompensationPayload(
                                item.compensationPayload() == null ? "" : item.compensationPayload())
                        .build();

        int attempt = 0;
        while (true) {
            long t0 = System.nanoTime();
            try {
                var response = resilientWorkerClient.compensateTask(request);
                orchestrationMetrics.recordCompensationExecutionTime(
                        Duration.ofNanos(System.nanoTime() - t0),
                        response.getSuccess() ? "success" : "logical_failure");
                if (response.getSuccess()) {
                    compensationPersistence.markCompensated(item.taskId());
                    orchestrationMetrics.recordCompensationSuccess();
                    return;
                }
                log.warn(
                        "Compensation RPC logical failure workflowId={} taskId={} message={}",
                        item.workflowId(),
                        item.taskId().getTaskId(),
                        response.getMessage());
            } catch (CallNotPermittedException e) {
                orchestrationMetrics.recordCompensationExecutionTime(
                        Duration.ofNanos(System.nanoTime() - t0), "circuit_open");
                log.warn(
                        "Worker circuit breaker open during compensation workflowId={} taskId={}",
                        item.workflowId(),
                        item.taskId().getTaskId());
            } catch (StatusRuntimeException e) {
                orchestrationMetrics.recordCompensationExecutionTime(
                        Duration.ofNanos(System.nanoTime() - t0), "rpc_error");
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
                orchestrationMetrics.recordCompensationTerminalFailure();
                return;
            }
            orchestrationMetrics.recordCompensationRetryAttempt();
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

