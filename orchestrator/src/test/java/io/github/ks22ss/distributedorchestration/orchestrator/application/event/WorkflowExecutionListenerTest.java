package io.github.ks22ss.distributedorchestration.orchestrator.application.event;

import static org.mockito.Mockito.verify;

import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.WorkflowExecutionUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkflowExecutionListenerTest {

    private final WorkflowExecutionUseCase executionUseCase = Mockito.mock(WorkflowExecutionUseCase.class);
    private final WorkflowExecutionListener listener = new WorkflowExecutionListener(executionUseCase);

    @Test
    void onWorkflowSubmittedTriggersExecution() {
        listener.onWorkflowSubmitted(new WorkflowSubmittedEvent("wf-1"));

        verify(executionUseCase).triggerExecution("wf-1");
    }

    @Test
    void onTaskCompletedTriggersExecution() {
        listener.onTaskCompleted(new TaskCompletedEvent("wf-1", "t-1"));

        verify(executionUseCase).triggerExecution("wf-1");
    }
}

