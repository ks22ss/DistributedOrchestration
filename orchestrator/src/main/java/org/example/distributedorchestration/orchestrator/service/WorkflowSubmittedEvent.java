package org.example.distributedorchestration.orchestrator.service;

/**
 * Published after a workflow and its tasks are committed; triggers execution.
 *
 * @param workflowId persisted workflow identifier
 */
public record WorkflowSubmittedEvent(String workflowId) {}
