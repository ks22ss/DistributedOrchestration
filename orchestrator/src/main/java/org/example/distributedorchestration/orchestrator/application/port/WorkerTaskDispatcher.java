package org.example.distributedorchestration.orchestrator.application.port;

import org.example.distributedorchestration.common.model.Task;

/** Dispatches a runnable task to a worker over gRPC. */
public interface WorkerTaskDispatcher {

    void dispatch(Task task);
}
