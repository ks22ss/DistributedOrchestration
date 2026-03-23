package org.example.distributedorchestration.orchestrator.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Task definition inside a workflow submission body.
 *
 * @param taskId unique id within the workflow
 * @param dependencies ids of tasks that must succeed before this task runs; may be null or empty
 * @param payload opaque task input
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitWorkflowTaskDto(
        @NotBlank String taskId,
        List<String> dependencies,
        String payload
) {}
