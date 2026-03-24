package org.example.distributedorchestration.worker.application.port;

/**
 * Worker task hooks for forward execution and compensation.
 */
public interface TaskExecutor {

    void execute();

    void compensate();
}
