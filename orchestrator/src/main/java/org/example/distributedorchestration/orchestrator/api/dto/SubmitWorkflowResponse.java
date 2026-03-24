package org.example.distributedorchestration.orchestrator.api.dto;

/**
 * Response from {@code POST /workflows}.
 *
 * @param workflowId persisted workflow id
 * @param status initial workflow status (e.g. RUNNING)
 */
public record SubmitWorkflowResponse(String workflowId, String status) {}
