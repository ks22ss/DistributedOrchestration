package org.example.distributedorchestration.orchestrator.application.event;

/**
 * Published after a workflow and its tasks are committed; triggers execution.
 *
 * @param workflowId persisted workflow identifier
 */
public record WorkflowSubmittedEvent(String workflowId) {}
