package io.github.ks22ss.distributedorchestration.worker.application.port.in;

/** Worker task hooks for forward execution and compensation. */
public interface TaskExecutor {

    void execute();

    void compensate();
}

