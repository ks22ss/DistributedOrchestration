package io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for {@code POST /workflows}.
 *
 * @param workflowId client-supplied workflow identifier
 * @param tasks task graph for this workflow
 */
public record SubmitWorkflowRequest(@NotBlank String workflowId, @NotEmpty @Valid List<SubmitWorkflowTask> tasks) {}

