package org.example.distributedorchestration.orchestrator.api.dto;

/**
 * Response after a workflow is accepted and persisted.
 *
 * @param workflowId persisted workflow id
 * @param status current workflow status (e.g. RUNNING after submit)
 */
public record SubmitWorkflowResponse(String workflowId, String status) {}
