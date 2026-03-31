package io.github.ks22ss.distributedorchestration.orchestrator.application.port.in;

public interface WorkflowExecutionUseCase {

    void triggerExecution(String workflowId);
}

