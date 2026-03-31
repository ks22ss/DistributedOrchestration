package io.github.ks22ss.distributedorchestration.orchestrator.adapters.integration.grpc;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.springframework.stereotype.Service;

/** Worker gRPC calls wrapped with Resilience4j circuit breaker. */
@Service
@RequiredArgsConstructor
public class ResilientWorkerGrpcClient {

    private final WorkerStubPool workerStubPool;

    @CircuitBreaker(name = "worker")
    public TaskResponse executeTask(TaskRequest request) {
        return workerStubPool.nextStub().executeTask(request);
    }

    @CircuitBreaker(name = "worker")
    public TaskResponse compensateTask(CompensationRequest request) {
        return workerStubPool.nextStub().compensateTask(request);
    }
}

