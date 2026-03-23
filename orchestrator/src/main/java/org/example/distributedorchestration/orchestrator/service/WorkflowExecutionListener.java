package org.example.distributedorchestration.orchestrator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kicks off execution only after the submit transaction successfully commits.
 */
@Component
@RequiredArgsConstructor
public class WorkflowExecutionListener {

    private final WorkflowExecutionService workflowExecutionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowSubmitted(WorkflowSubmittedEvent event) {
        workflowExecutionService.triggerExecution(event.workflowId());
    }
}
