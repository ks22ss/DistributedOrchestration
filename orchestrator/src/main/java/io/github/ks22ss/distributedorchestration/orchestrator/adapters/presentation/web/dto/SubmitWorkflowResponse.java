package io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.web.dto;

/**
 * Response from {@code POST /workflows}.
 *
 * @param workflowId persisted workflow id
 * @param status initial workflow status (e.g. RUNNING)
 */
public record SubmitWorkflowResponse(String workflowId, String status) {}

