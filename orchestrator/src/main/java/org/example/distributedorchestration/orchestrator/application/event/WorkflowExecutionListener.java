package org.example.distributedorchestration.orchestrator.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.orchestrator.application.service.WorkflowExecutionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kicks off execution only after the submit transaction successfully commits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionListener {

    private final WorkflowExecutionService workflowExecutionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowSubmitted(WorkflowSubmittedEvent event) {
        log.debug("Workflow submitted event received workflowId={}", event.workflowId());
        workflowExecutionService.triggerExecution(event.workflowId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.debug(
                "Task completed event received workflowId={} taskId={}",
                event.workflowId(),
                event.taskId());
        workflowExecutionService.triggerExecution(event.workflowId());
    }
}
