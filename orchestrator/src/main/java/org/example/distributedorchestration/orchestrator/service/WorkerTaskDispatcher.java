package org.example.distributedorchestration.orchestrator.service;

import org.example.distributedorchestration.common.model.Task;

/**
 * Dispatches a runnable task to a worker (gRPC in a later step).
 */
public interface WorkerTaskDispatcher {

    void dispatch(Task task);
}
