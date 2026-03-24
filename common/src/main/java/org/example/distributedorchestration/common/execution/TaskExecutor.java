package org.example.distributedorchestration.common.execution;

/**
 * Saga-style task hooks (spec Step 11): forward {@link #execute()} and compensating {@link #compensate()}.
 */
public interface TaskExecutor {

    void execute();

    void compensate();
}
