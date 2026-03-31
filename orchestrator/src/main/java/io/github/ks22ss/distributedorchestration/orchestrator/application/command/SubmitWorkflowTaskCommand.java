package io.github.ks22ss.distributedorchestration.orchestrator.application.command;

import java.util.List;

public record SubmitWorkflowTaskCommand(
        String taskId,
        List<String> dependencies,
        String payload,
        String compensationPayload
) {}

