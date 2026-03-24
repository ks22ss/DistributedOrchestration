package org.example.distributedorchestration.orchestrator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowCompensationAsyncRunner {

    private final WorkflowCompensationService workflowCompensationService;

    @Async("compensationExecutor")
    public void triggerCompensation(String workflowId) {
        workflowCompensationService.compensateAfterTerminalTaskFailure(workflowId);
    }
}
