package org.example.distributedorchestration.orchestrator.grpc;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.stereotype.Service;

/**
 * Worker gRPC calls wrapped with Resilience4j circuit breaker (spec Step 12).
 */
@Service
@RequiredArgsConstructor
public class ResilientWorkerGrpcClient {

    private final WorkerServiceGrpc.WorkerServiceBlockingStub workerStub;

    @CircuitBreaker(name = "worker")
    public TaskResponse executeTask(TaskRequest request) {
        return workerStub.executeTask(request);
    }

    @CircuitBreaker(name = "worker")
    public TaskResponse compensateTask(CompensationRequest request) {
        return workerStub.compensateTask(request);
    }
}
