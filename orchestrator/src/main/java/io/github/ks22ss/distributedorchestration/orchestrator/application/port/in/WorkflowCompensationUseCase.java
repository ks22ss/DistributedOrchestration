package io.github.ks22ss.distributedorchestration.orchestrator.application.port.in;

public interface WorkflowCompensationUseCase {

    void compensateAfterTerminalTaskFailure(String workflowId);

    void resumeStuckCompensation(String workflowId);
}

