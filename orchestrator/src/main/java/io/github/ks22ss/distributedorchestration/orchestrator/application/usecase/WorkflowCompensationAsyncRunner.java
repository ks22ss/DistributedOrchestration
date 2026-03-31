package io.github.ks22ss.distributedorchestration.orchestrator.application.usecase;

import lombok.RequiredArgsConstructor;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.WorkflowCompensationUseCase;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowCompensationAsyncRunner {

    private final WorkflowCompensationUseCase workflowCompensationService;

    @Async("compensationExecutor")
    public void triggerCompensation(String workflowId) {
        workflowCompensationService.compensateAfterTerminalTaskFailure(workflowId);
    }
}

