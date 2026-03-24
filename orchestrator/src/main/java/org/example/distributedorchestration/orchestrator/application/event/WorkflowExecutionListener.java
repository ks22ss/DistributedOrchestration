package org.example.distributedorchestration.orchestrator.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.orchestrator.application.service.WorkflowExecutionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kicks off execution after workflow submit commits, and after each task SUCCESS is committed.
 *
 * <p>{@link TaskCompletedEvent} uses {@link EventListener} because it is published from a
 * {@code REQUIRES_NEW} transaction's {@code afterCommit} callback (no enclosing transaction), so
 * {@link TransactionalEventListener} would not run reliably.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionListener {

    private final WorkflowExecutionService workflowExecutionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowSubmitted(WorkflowSubmittedEvent event) {
        log.info("Workflow submitted event received workflowId={}", event.workflowId());
        workflowExecutionService.triggerExecution(event.workflowId());
    }

    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        log.info(
                "Task completed event received workflowId={} taskId={}",
                event.workflowId(),
                event.taskId());
        workflowExecutionService.triggerExecution(event.workflowId());
    }
}
