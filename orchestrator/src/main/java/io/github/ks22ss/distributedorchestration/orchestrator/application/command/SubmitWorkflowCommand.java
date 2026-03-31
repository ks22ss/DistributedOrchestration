package io.github.ks22ss.distributedorchestration.orchestrator.application.command;

import java.util.List;

public record SubmitWorkflowCommand(String workflowId, List<SubmitWorkflowTaskCommand> tasks) {}

